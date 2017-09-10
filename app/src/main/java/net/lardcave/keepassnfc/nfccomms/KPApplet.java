package net.lardcave.keepassnfc.nfccomms;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class KPApplet {
	private static final String LOG_TAG = "KPNFC KPApplet";
	private final static byte CLA_CARD_KPNFC_CMD = (byte) 0xB0;

	private final static byte INS_CARD_GET_CARD_PUBKEY = (byte) 0x70;
	private final static byte INS_CARD_SET_PASSWORD_KEY = (byte) 0x71;
	private final static byte INS_CARD_PREPARE_DECRYPTION = (byte) 0x72;
	private final static byte INS_CARD_DECRYPT_BLOCK = (byte) 0x73;
	//private final static byte INS_CARD_GET_VERSION = (byte) 0x74;
	//private final static byte INS_CARD_GENERATE_CARD_KEY = (byte) 0x75;
	private final static byte INS_CARD_WRITE_TO_SCRATCH = (byte) 0x76;

	//private final static byte RESPONSE_SUCCEEDED = (byte) 0x1;
	//private final static byte RESPONSE_FAILED = (byte) 0x2;

	private static final byte OFFSET_CLA = 0x00;
	private static final byte OFFSET_INS = 0x01;
	private static final byte OFFSET_P1 = 0x02;
	private static final byte OFFSET_P2 = 0x03;
	private static final byte OFFSET_LC = 0x04;
	private static final byte OFFSET_DATA = 0x05;
	private static final byte HEADER_LENGTH = 0x05;

	private static final int MAX_CHUNK_SIZE = 120;

	// AID of the KPNFC decryptor: f0 37 54 72  80 4f d5 fa  0f 24 3e 42  c1 b6 38 25
	private static final byte[] selectKPNFCAppletAPDU = {
			(byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00, (byte) 0x10,
			(byte) 0xf0, (byte) 0x37, (byte) 0x54, (byte) 0x72, (byte) 0x80, (byte) 0x4f, (byte) 0xd5, (byte) 0xfa, // AID
			(byte) 0x0f, (byte) 0x24, (byte) 0x3e, (byte) 0x42, (byte) 0xc1, (byte) 0xb6, (byte) 0x38, (byte) 0x25, // AID
			(byte) 0x00,
	};

	private static final byte[] selectNdefAppletAPDU = {
			(byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00, (byte) 0x07,
			(byte) 0xD2, (byte) 0x76, (byte) 0x00, (byte) 0x00, (byte) 0x85, (byte) 0x01, (byte) 0x01,
			(byte) 0x00,
	};

	private static final byte[] selectNdefDataAPDU = {
			(byte) 0x00, (byte) 0xA4, (byte) 0x00, (byte) 0x0C, (byte) 0x02,
			(byte) 0xE1, (byte) 0x04,
			(byte) 0x00,
	};

	private SecureRandom rng = new SecureRandom();

	private IsoDep connect(Intent intent) throws IOException {
		Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

		IsoDep channel = IsoDep.get(tag);
		if(channel == null) {
			return null;
		}

		channel.connect();

		// setPasswordKey() asks the JavaCard to do RSA decryption, which takes a while, but
		// it's hard to say exactly how long.
		channel.setTimeout(2000);

		channel.transceive(selectKPNFCAppletAPDU);

		return channel;
	}

	public boolean write(Intent intent, byte[] secret) throws IOException {
		IsoDep channel = connect(intent);
		if(channel == null) {
			return false;
		} else {

			setPasswordKey(channel, secret);

			// Attempt to write NDEF as well.
			writeNdef(channel, KPNdef.createWakeOnlyNdefMessage());
			channel.close();

			return true;
		}
	}

	private boolean writeNdef(IsoDep channel, NdefMessage ndefMessage) throws IOException {
		byte[] result;

		byte[] ndefData = ndefMessage.toByteArray();
		byte[] ndefWriteBinary = new byte[ndefData.length + 7];
		ndefWriteBinary[0] = (byte) 0x00;
		ndefWriteBinary[1] = (byte) 0xD6; // UPDATE BINARY
		ndefWriteBinary[2] = (byte) 0x00; // offset MSB
		ndefWriteBinary[3] = (byte) 0x00; // offset LSB
		ndefWriteBinary[4] = (byte) (ndefData.length + 2);
		ndefWriteBinary[5] = (byte) 0; // MSB of ndef data
		ndefWriteBinary[6] = (byte) (ndefData.length);
		System.arraycopy(ndefData, 0, ndefWriteBinary, 7, ndefData.length);

		byte[][] commands = {selectNdefAppletAPDU, selectNdefDataAPDU, ndefWriteBinary};
		for(byte[] command: commands) {
			//Log.d(LOG_TAG, "write NDEF command " + toHex(command));

			result = channel.transceive(command);
			if(!resultWasSuccess(result, 2)) {
				//Log.d(LOG_TAG, "NDEF command failed: " + toHex(command) + " ( got " + toHex(result) + ")");
				return false;
			}
		}

		return true;
	}

	public byte[] decrypt(Intent intent, byte[] encrypted) throws IOException {
		if(encrypted.length % 16 != 0) {
			Log.d(LOG_TAG, "Encrypted bytes not a multiple of AES block size");
			return null;
		}

		byte[] passwordKeyIv = new byte[16]; // All zeroes -- TODO co-ordinate this with DatabaseInfo.

		IsoDep channel = connect(intent);
		if(channel == null) {
			return null;
		} else {

			byte[] decrypted = decrypt_internal(channel, passwordKeyIv, encrypted);

			channel.close();

			return decrypted;
		}
	}

	private byte[] randomBytes(int amt) {
		byte[] bytes = new byte[amt];

		rng.nextBytes(bytes);
		return bytes;
	}

	private byte[] decrypt_internal(IsoDep channel, byte[] passwordKeyIv, byte[] encrypted) throws IOException {
		// Generate a random transaction key and IV.
		byte[] transactionKey = randomBytes(16);
		byte[] transactionIv = randomBytes(16);

		// Connect to the card and establish a transaction key.
		byte[] transactionParameters = new byte[32];

		// Prepare decryption: 16 bytes of transaction key, encrypted with the card key,
		// followed by two IVs.

		byte[] encryptedTransactionKey = encryptWithCardKey(channel, transactionKey);
		if(encryptedTransactionKey == null) {
			return null;
		}
		// The encrypted transaction key is too large (256 bytes for a 2048-bit RSA key) to fit
		// in one APDU, so write it to the card's scratch area in pieces.
		writeToScratchArea(channel, encryptedTransactionKey);

		// Now that the encrypted key is in the scratch area, tell the card to decrypt the
		// transaction key and prepare for decryption using the password key.
		System.arraycopy(transactionIv, 0, transactionParameters, 0, 16);
		System.arraycopy(passwordKeyIv, 0, transactionParameters, 16, 16);
		channel.transceive(constructApdu(INS_CARD_PREPARE_DECRYPTION, transactionParameters));

		int max_chunk_size_aes = (MAX_CHUNK_SIZE / 16) * 16;

		// Decryption has been initialised, so ask the card to decrypt the text.
		byte[] transactionKeyEncrypted = new byte[encrypted.length];

		for(int idx = 0; idx < encrypted.length; idx += max_chunk_size_aes) {
			int amt = encrypted.length - idx;
			if(amt > max_chunk_size_aes)
				amt = max_chunk_size_aes;

			byte[] chunk = new byte[amt];
			System.arraycopy(encrypted, idx, chunk, 0, amt);

			byte[] response = channel.transceive(constructApdu(INS_CARD_DECRYPT_BLOCK, chunk));
			if(response.length > 1 && response[0] == (byte)0x1) {
				System.arraycopy(response, 1, transactionKeyEncrypted, idx, amt);
			}
		}

		// This is encrypted with the transaction key, so decrypt it.
		return decryptWithTransactionKey(transactionKeyEncrypted, transactionKey, transactionIv);
	}

	private byte[] decryptWithTransactionKey(byte[] source, byte[] keyBytes, byte[] ivBytes)
	{
		Cipher cipher;

		try {
			cipher = Cipher.getInstance("AES/CBC/NoPadding");
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			e.printStackTrace();
			return null;
		}

		SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
		IvParameterSpec iv = new IvParameterSpec(ivBytes);
		try {
			cipher.init(Cipher.DECRYPT_MODE, key, iv);
		} catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
			e.printStackTrace();
			return null;
		}

		byte[] result;
		try {
			result = cipher.doFinal(source, 0, source.length);
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			e.printStackTrace();
			return null;
		}

		return result;
	}

	private boolean resultWasSuccess(byte[] result, int expectedLength) {
		if(expectedLength != result.length)
			return false;

		switch(expectedLength) {
			case 2:
				return (result[0] == (byte)0x90 && result[1] == (byte)0x00);
			case 3:
				return (result[0] == (byte)0x1 && result[1] == (byte)0x90 && result[2] == (byte)0x00);
			default:
				throw new RuntimeException("Unexpected expectedLength");
		}
	}

	private void setPasswordKey(IsoDep channel, byte[] passwordKey) throws IOException {
		byte[] encryptedPasswordKey = encryptWithCardKey(channel, passwordKey);
		if(encryptedPasswordKey == null) {
			return;
		}

		writeToScratchArea(channel, encryptedPasswordKey);

		byte[] command = constructApdu(INS_CARD_SET_PASSWORD_KEY);
		channel.transceive(command);
	}

	private void writeToScratchArea(IsoDep channel, byte[] data) throws IOException {
		for(int offset = 0; offset < data.length; offset += MAX_CHUNK_SIZE) {
			int amount = data.length - offset;
			if(amount > MAX_CHUNK_SIZE)
				amount = MAX_CHUNK_SIZE;

			byte[] args = new byte[amount + 2];
			putShort(args, 0, (short)offset);

			System.arraycopy(data, offset, args, 2, amount);

			byte[] command = constructApdu(INS_CARD_WRITE_TO_SCRATCH, args);
			channel.transceive(command);
		}
	}

	private byte[] encryptWithCardKey(IsoDep channel, byte[] input) throws IOException {
		RSAPublicKey publicKey = getCardPubKey(channel);
		if(publicKey == null) {
			System.err.println("Key invalid, can't encrypt with card key");
			return null;
		}

		Cipher cipher;

		try {
			cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			System.err.println("RSA cipher not supported");
			return null;
		}

		try {
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		} catch (InvalidKeyException e) {
			System.err.println("Invalid key");
			return null;
		}

		try {
			return cipher.doFinal(input);
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			System.err.println("Couldn't encrypt with card key:");
			e.printStackTrace();
			return null;
		}
	}

	private RSAPublicKey getCardPubKey(IsoDep channel) throws IOException {
		byte[] args = new byte[3];

		args[0] = 1; // get exponent
		args[1] = 0;
		args[2] = 0;
		byte[] command = constructApdu(INS_CARD_GET_CARD_PUBKEY, args);
		byte[] result = channel.transceive(command);

		if(result == null || result[0] != 1) {
			System.err.println("Couldn't retrieve exponent");
			return null;
		}

		BigInteger exponent = new BigInteger(1, Arrays.copyOfRange(result, 3, result[2] + 3));

		List<byte[]> modulusPortions = new ArrayList<>();
		args[0] = 2; // get modulus
		short offset = 0, bytesToGo;
		do {
			putShort(args, 1, offset);
			command = constructApdu(INS_CARD_GET_CARD_PUBKEY, args);
			result = channel.transceive(command);

			if (result == null || result[0] != 1) {
				System.err.println("Couldn't retrieve modulus");
				return null;
			}
			int bytesSent = getShort(result, 1);
			bytesToGo = getShort(result, 3);

			modulusPortions.add(Arrays.copyOfRange(result, 5, result.length - 2)); // exclude result code
			offset += bytesSent;
		} while(bytesToGo > 0);

		byte[] modulusBytes = new byte[offset];
		offset = 0;
		for(byte[] portion: modulusPortions) {
			System.arraycopy(portion, 0, modulusBytes, offset, portion.length);
			offset += portion.length;
		}

		BigInteger modulus = new BigInteger(1, modulusBytes);

		// Turn these numbers into a crypto-api-friendly PublicKey object.

		KeyFactory keyFactory;
		try {
			keyFactory = KeyFactory.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("Couldn't create RSA keyfactory"); // which would be very strange
			return null;
		}

		RSAPublicKey publicKey;
		try {
			publicKey = (RSAPublicKey) keyFactory.generatePublic(new RSAPublicKeySpec(modulus, exponent));
		} catch (InvalidKeySpecException e) {
			System.err.println("Couldn't produce a PublicKey object");
			return null;
		}

		return publicKey;
	}

	private static byte[] constructApdu(byte command) {
		byte[] nothing = {};
		return constructApdu(command, nothing);
	}

	private static byte[] constructApdu(byte command, byte[] data)
	{
		byte[] apdu = new byte[HEADER_LENGTH + data.length];
		apdu[OFFSET_CLA] = CLA_CARD_KPNFC_CMD;
		apdu[OFFSET_INS] = command;
		apdu[OFFSET_P1] = (byte)0;
		apdu[OFFSET_P2] = (byte)0;
		apdu[OFFSET_LC] = (byte)data.length;

		System.arraycopy(data, 0, apdu, OFFSET_DATA, data.length);

		return apdu;
	}

	private short getShort(byte[] buffer, int idx) {
		// assumes big-endian which seems to be how JavaCard rolls
		return (short)( (((buffer[idx] & 0xff) << 8) | (buffer[idx + 1] & 0xff) ));
	}

	private void putShort(byte[] args, int idx, short val) {
		args[idx] = (byte)((val & 0xff) >> 8);
		args[idx + 1] = (byte)(val & 0xff);
	}

	@SuppressWarnings("unused")
    public static String toHex(byte[] data) {
		StringBuilder buf = new StringBuilder();

		for(byte b: data) {
			buf.append(nibbleToChar((byte)((b & 0xff) >> 4))); // java is bs
			buf.append(nibbleToChar((byte)(b & 0xf)));
			buf.append(' ');
		}

		return buf.toString();
	}

	private static char nibbleToChar(byte nibble) {
		if(nibble >= 16) {
			throw new RuntimeException("nibbleToChar: value >= 16");
		}

		if(nibble < 10)
			return (char)('0' + nibble);
		else
			return (char)('A' + (nibble - 10));
	}


}
