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
import java.util.Arrays;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.content.Context;

/* Represents the on-disk database info including encrypted password */

public class DatabaseInfo {
	public Uri database;
	public Uri keyfile;
	public String password;
	public int config;

	private static final String CIPHER = "AES/CTR/NoPadding";
    private static final String LOG_TAG = "keepassnfc";
	
	public DatabaseInfo(Uri database, Uri keyfile, String password, int config)
	{
		this.database = database;
		this.keyfile = keyfile;
		this.password = password;
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
		byte[] padded_password = new byte[Settings.max_password_length];
		byte[] plaintext_password = password.getBytes();
		SecureRandom rng = new SecureRandom();		
		
		// Password length...
		padded_password[idx ++] = (byte)password.length();
		// ... and password itself...
		for (i = 0; i < plaintext_password.length; i++) 
			padded_password[idx ++] = plaintext_password[i];
		// ... and random bytes to pad.
		while (idx < padded_password.length)
			padded_password[idx++] = (byte)rng.nextInt();
		
		// Encrypt everything
		Cipher cipher = get_cipher(key, Cipher.ENCRYPT_MODE);
		try {
			return cipher.doFinal(padded_password);
		} catch (javax.crypto.IllegalBlockSizeException e) {
			Log.d(LOG_TAG, "IllegalBlockSize");
			throw new CryptoFailedException();
		} catch (javax.crypto.BadPaddingException e) {
			Log.d(LOG_TAG, "BadPadding");
			throw new CryptoFailedException();
		}
	}
	
	private static String decrypt_password(byte[] crypted_password, byte[] key) throws CryptoFailedException
	{
		byte[] decrypted;
		Cipher cipher = get_cipher(key, Cipher.DECRYPT_MODE);

		try {
			decrypted = cipher.doFinal(crypted_password);
		} catch (javax.crypto.IllegalBlockSizeException e) {
			Log.d(LOG_TAG, "IllegalBlockSize");
			throw new CryptoFailedException();
		} catch (javax.crypto.BadPaddingException e) {
			Log.d(LOG_TAG, "BadPadding");
			throw new CryptoFailedException();
		}

		int length = (int)decrypted[0];
		return new String(decrypted, 1, length);
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
	
	private static short read_short(FileInputStream fis) throws IOException
	{
		byte[] bytes = new byte[2];
		short[] shorts = new short[1];
		fis.read(bytes, 0, 2);

		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
		return shorts[0];
	}

	// Persist access to the file.
	protected void persistAccessToFile(Context ctx, Uri uri) {
		// https://developer.android.com/guide/topics/providers/document-provider.html#permissions
		// via http://stackoverflow.com/a/21640230
		ctx.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
	}

	public void retainOrUpdateUriAccess(Context ctx) {
		if(database != null)
			persistAccessToFile(ctx, database);

		if(keyfile != null)
			persistAccessToFile(ctx, keyfile);
	}

	public boolean serialise(Context ctx, byte[] key) throws CryptoFailedException
	{
		/* Encrypt the data and store it on the Android device.
		 *
		 * The encryption key is stored on the NFC tag.
		*/
		byte encrypted_config;
		byte[] encrypted_password = encrypt_password(key);

		retainOrUpdateUriAccess(ctx);

		FileOutputStream nfcinfo;
		try {
			nfcinfo = ctx.openFileOutput(Settings.nfcinfo_filename_template + "_00.txt", Context.MODE_PRIVATE);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
		try {
			String uriString;

			nfcinfo.write(config);

			uriString = database.toString();
			nfcinfo.write(to_short(uriString.length()));
			nfcinfo.write(uriString.getBytes());

			if (keyfile == null) {
				nfcinfo.write(to_short(0));
			} else {
				uriString = keyfile.toString();
				nfcinfo.write(to_short(uriString.length()));
				nfcinfo.write(uriString.getBytes());
			}

			nfcinfo.write(to_short(encrypted_password.length));
			nfcinfo.write(encrypted_password);
			nfcinfo.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		return true;
	}
	
	public static DatabaseInfo deserialise(Context ctx, byte[] key) throws CryptoFailedException
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
		
		password = decrypt_password(encrypted_password, key);

		DatabaseInfo dbInfo = new DatabaseInfo(database, keyfile, password, config);
		dbInfo.retainOrUpdateUriAccess(ctx);
		return dbInfo;
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
