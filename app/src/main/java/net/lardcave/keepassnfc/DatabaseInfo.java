/*
 * This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to [http://unlicense.org]
 */

package net.lardcave.keepassnfc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.Cipher;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.content.Context;

/* Represents the on-disk database info including encrypted password */

class DatabaseInfo {
	Uri database;
	Uri keyfile;
	String password;
	byte[] encrypted_password;

	public int config;

	private static final String CIPHER = "AES/CBC/NoPadding";
    private static final String LOG_TAG = "keepassnfc";
	
	public DatabaseInfo(Uri database, Uri keyfile, String password, int config)
	{
		this.database = database;
		this.keyfile = keyfile;
		this.password = password;
		this.encrypted_password = new byte[Settings.max_password_length];
		this.config = config;
	}

	public DatabaseInfo(Uri database, Uri keyfile, byte[] encrypted_password, int config)
	{
		this.database = database;
		this.keyfile = keyfile;
		this.password = null;
		this.encrypted_password = encrypted_password;
		this.config = config;
	}

	private static Cipher get_cipher(byte[] key, int mode) throws CryptoFailedException
	{
		try {
			SecretKeySpec sks = new SecretKeySpec(key, "AES");
			Cipher cipher = Cipher.getInstance(CIPHER);
			// No IV as key is never re-used
			byte[] iv_bytes = new byte[cipher.getBlockSize()]; // zeroes
			IvParameterSpec iv = new IvParameterSpec(iv_bytes);

			cipher.init(mode, sks, iv);

			return cipher;
		} catch (java.security.NoSuchAlgorithmException e) {
			Log.d(LOG_TAG, "NoSuchAlgorithm");
			throw new CryptoFailedException();
		} catch (java.security.InvalidKeyException e) {
			Log.d(LOG_TAG, "InvalidKey");
			throw new CryptoFailedException();
		} catch (javax.crypto.NoSuchPaddingException e) {
			Log.d(LOG_TAG, "NoSuchPadding");
			throw new CryptoFailedException();
		} catch (java.security.InvalidAlgorithmParameterException e) {
			Log.d(LOG_TAG, "InvalidAlgorithmParameter");
			throw new CryptoFailedException();
		}
	}

	private byte[] encrypt_password(byte[] key) throws CryptoFailedException
	{
		int i;
		int idx = 0;
		byte[] plaintext_password = password.getBytes();
		SecureRandom rng = new SecureRandom();		
		
		// Password length...
		encrypted_password[idx ++] = (byte)password.length();
		// ... and password itself...
		for (i = 0; i < plaintext_password.length; i++)
			encrypted_password[idx ++] = plaintext_password[i];
		// ... and random bytes to pad.
		while (idx < encrypted_password.length)
			encrypted_password[idx++] = (byte)rng.nextInt();
		
		// Encrypt everything
		Cipher cipher = get_cipher(key, Cipher.ENCRYPT_MODE);
		try {
			return cipher.doFinal(encrypted_password);
		} catch (javax.crypto.IllegalBlockSizeException e) {
			Log.d(LOG_TAG, "IllegalBlockSize");
			throw new CryptoFailedException();
		} catch (javax.crypto.BadPaddingException e) {
			Log.d(LOG_TAG, "BadPadding");
			throw new CryptoFailedException();
		}
	}

	public String set_decrypted_password(byte[] decrypted_bytes) {
		int length = (int)decrypted_bytes[0];
		password = new String(decrypted_bytes, 1, length);
		return password;
	}
	
	public String decrypt_password(byte[] key) throws CryptoFailedException
	{
		byte[] decrypted;
		Cipher cipher = get_cipher(key, Cipher.DECRYPT_MODE);

		try {
			decrypted = cipher.doFinal(encrypted_password);
		} catch (javax.crypto.IllegalBlockSizeException e) {
			Log.d(LOG_TAG, "IllegalBlockSize");
			throw new CryptoFailedException();
		} catch (javax.crypto.BadPaddingException e) {
			Log.d(LOG_TAG, "BadPadding");
			throw new CryptoFailedException();
		}

		return set_decrypted_password(decrypted);
	}
	
	private byte[] to_short(short i)
	{
		byte[] bytes = new byte[2];
		short[] shorts = {i};

		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts);

		return bytes;
	}

	private byte[] to_short(int i)
	{
		return to_short((short)i);
	}
	
	// Persist access to the file.
	private void persistAccessToFile(Context ctx, Uri uri) {
		// https://developer.android.com/guide/topics/providers/document-provider.html#permissions
		// via http://stackoverflow.com/a/21640230
		try {
			ctx.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}

	private void retainOrUpdateUriAccess(Context ctx) {
		if(database != null)
			persistAccessToFile(ctx, database);

		if(keyfile != null)
			persistAccessToFile(ctx, keyfile);
	}

	boolean serialise(Context ctx, byte[] key) throws CryptoFailedException
	{
		/* Encrypt the configuration (database, password, key location) and store it on the Android device.
		 *
		 * The encryption key is stored on the NFC tag.
		*/
		byte encrypted_config;
		encrypted_password = encrypt_password(key);

		retainOrUpdateUriAccess(ctx);

		FileOutputStream configuration;
		try {
			configuration = ctx.openFileOutput(Settings.nfcinfo_filename_template + "_00.txt", Context.MODE_PRIVATE);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
		try {
			String uriString;

			configuration.write(config);

			uriString = database.toString();
			configuration.write(to_short(uriString.length()));
			configuration.write(uriString.getBytes());

			if (keyfile == null) {
				configuration.write(to_short(0));
			} else {
				uriString = keyfile.toString();
				configuration.write(to_short(uriString.length()));
				configuration.write(uriString.getBytes());
			}

			configuration.write(to_short(encrypted_password.length));
			configuration.write(encrypted_password);
			configuration.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}
	
	public static DatabaseInfo deserialise(Context ctx)
	{
		int config = Settings.CONFIG_NOTHING;
		String databaseString, keyfileString, password;
		byte[] buffer = new byte[1024];
		byte[] encrypted_password = new byte[Settings.max_password_length];

		FileInputStream nfcinfo;

		try {
			nfcinfo = ctx.openFileInput(Settings.nfcinfo_filename_template + "_00.txt");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		
		try {
			config = nfcinfo.read();
			databaseString = read_string(nfcinfo, buffer);
			keyfileString = read_string(nfcinfo, buffer);
			read_bytes(nfcinfo, encrypted_password);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		Uri database = Uri.parse(databaseString);
		Uri keyfile = keyfileString.equals("") ? null: Uri.parse(keyfileString);
		
		DatabaseInfo dbInfo = new DatabaseInfo(database, keyfile, encrypted_password, config);
		dbInfo.retainOrUpdateUriAccess(ctx);
		return dbInfo;
	}

	private static short read_short(FileInputStream fis) throws IOException
	{
		byte[] bytes = new byte[2];
		short[] shorts = new short[1];
		fis.read(bytes, 0, 2);

		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
		return shorts[0];
	}

	private static int read_bytes(FileInputStream fis, byte[] buffer) throws IOException
	{
		int length = read_short(fis);
		
		fis.read(buffer, 0, length);
		return length;		
	}
	
	private static String read_string(FileInputStream fis, byte[] buffer) throws IOException
	{
		int length = read_bytes(fis, buffer);
		return new String(buffer, 0, length);
	}
}
