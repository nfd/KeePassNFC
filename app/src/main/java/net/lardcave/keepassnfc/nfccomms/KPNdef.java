package net.lardcave.keepassnfc.nfccomms;

import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Parcelable;

import net.lardcave.keepassnfc.Settings;

import java.io.IOException;
import java.util.Arrays;

public class KPNdef {
	private static final String nfc_ndef_mime_type = "application/x-keepassnfc-3";

	// Stored on the tag:
	private static final int KEY_TYPE_RAW = 1; // Password is stored directly on the card (basic NTAG203 memory-only tag)
	private static final int KEY_TYPE_APP = 2; // Password is stored in the KeepassNFC applet (JavaCard smartcard).
	private static final int key_type_length = 1;

	private byte[] secretKey;
	private boolean _successfulNdefRead = false;

	/** Construct an NDEF message for writing containing a secret key. */
	public KPNdef(byte[] secretKey) {
		this.secretKey = secretKey;
	}

	/** Construct an NDEF message for writing without any secret information (for use with applet) */
	public KPNdef() {
		// TODO this is unused because the applet code bypasses Android's NDEF support (since it
		// TODO already has an isodep channel open). harmonise this & applet NDEF code.
		this.secretKey = null;
	}

	public static IntentFilter getIntentFilter() {
		IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
		try {
			ndef.addDataType(nfc_ndef_mime_type);
		}
		catch (IntentFilter.MalformedMimeTypeException e) {
			throw new RuntimeException("fail", e);
		}

		return ndef;
	}

	/** Read an NDEF message from an Intent */
	public KPNdef(Intent intent) {
		Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
		if (rawMsgs != null) {
			NdefMessage [] msgs = new NdefMessage[rawMsgs.length];
			for (int j = 0; j < rawMsgs.length; j++) {
				msgs[j] = (NdefMessage) rawMsgs[j];
				NdefRecord record = msgs[j].getRecords()[0];
				if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA)
				{
					String mimetype = record.toMimeType();
					if (mimetype.equals(nfc_ndef_mime_type)) {
						_successfulNdefRead = true;
						decodePayload(record.getPayload());
					}
				}
			}
		}
	}

	public boolean readWasSuccessful() {
		return _successfulNdefRead;
	}

	public byte[] getSecretKey() {
		return secretKey;
	}

	/** Write NDEF to the tag to wake up KPNFC when it's presented. */
	public boolean write(Tag tag) {
		if(secretKey == null) {
			return writeMessageInternal(tag, createWakeOnlyNdefMessage());
		} else {
			return writeMessageInternal(tag, createRandomBytesNdefMessage(secretKey));
		}
	}

	private void decodePayload(byte[] payload) {
		switch(payload[0]) {
			case KEY_TYPE_RAW:
				secretKey = Arrays.copyOfRange(payload, 1, payload.length);
				break;
			case KEY_TYPE_APP:
				secretKey = null;
				break;
		}
	}

	private static boolean writeMessageInternal(Tag tag, NdefMessage message) {
		// Write the payload to the tag.
		android.nfc.tech.Ndef ndef = android.nfc.tech.Ndef.get(tag);
		try {
			ndef.connect();
			ndef.writeNdefMessage(message);
			ndef.close();
			return true;
		} catch (IOException | FormatException e) {
			e.printStackTrace();
		}

		return false;
	}

	private static NdefMessage createRandomBytesNdefMessage(byte[] secretKey)
	{
		byte[] messageBytes;

		messageBytes = new byte[key_type_length + Settings.key_length];
		messageBytes[0] = (byte)KEY_TYPE_RAW;
		System.arraycopy(secretKey, 0, messageBytes, 1, Settings.key_length);

		return ndefFromBytes(messageBytes);
	}

	static NdefMessage createWakeOnlyNdefMessage()
	{
		byte[] messageBytes;

		messageBytes = new byte[key_type_length];
		messageBytes[0] = (byte)KEY_TYPE_APP;

		return ndefFromBytes(messageBytes);
	}

	private static NdefMessage ndefFromBytes(byte[] messageBytes) {
		NdefRecord ndef_records = NdefRecord.createMime(nfc_ndef_mime_type, messageBytes);
		return new NdefMessage(ndef_records);
	}

}
