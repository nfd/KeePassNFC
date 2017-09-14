package net.lardcave.keepassnfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcF;
import android.util.Log;

import net.lardcave.keepassnfc.keepassapp.KeePassApp;
import net.lardcave.keepassnfc.keepassapp.KeePassApps;
import net.lardcave.keepassnfc.nfccomms.KPApplet;
import net.lardcave.keepassnfc.nfccomms.KPNdef;

import java.io.IOException;

class NfcReadActions {
	static class Error extends Exception {
		Error(String message) {
			super(message);
		}

	}

	static IntentFilter[] getFilters() {
		IntentFilter[] filters = new IntentFilter[] {KPNdef.getIntentFilter(), KPApplet.getIntentFilter()};

		return filters;
	}

	static String[][] getTechLists() {
		return new String[][] { new String[] { NfcF.class.getName(), IsoDep.class.getName()} };
	}

	static void nfc_enable(Activity activity)
	{
		// Register for any NFC event (only while we're in the foreground)

		NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity);
		PendingIntent pending_intent = PendingIntent.getActivity(activity, 0, new Intent(activity, activity.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		adapter.enableForegroundDispatch(activity, pending_intent, getFilters(), getTechLists());
	}

	static void nfc_disable(Activity activity)
	{
		NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity);

		adapter.disableForegroundDispatch(activity);
	}

	private static void decryptUsingApplet(Intent intent, DatabaseInfo dbinfo) throws Error {
		KPApplet applet = new KPApplet();
		byte[] decrypted_bytes = null;
		try {
			decrypted_bytes = applet.decrypt(intent, dbinfo.encrypted_password);
		} catch (IOException e) {
			throw new Error("Card communication failed.");
		}

		if (decrypted_bytes != null) {
			dbinfo.set_decrypted_password(decrypted_bytes);
		}
	}

	static DatabaseInfo getDbInfoFromIntent(Context ctx, Intent intent) throws Error {
		KPNdef ndef = null;
		DatabaseInfo dbinfo = DatabaseInfo.deserialise(ctx);

		if(dbinfo == null) {
			throw new Error("No KPNFC database set up.");
		}

		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
			/* Attempt to decode using NDEF. */
			ndef = new KPNdef(intent);

			if (ndef.readWasSuccessful()) {
				byte[] secretKey = ndef.getSecretKey();
				if (secretKey != null) {
				/* NDEF message contained the decryption key -- don't use applet. */
					try {
						dbinfo.decrypt_password(secretKey);
					} catch (CryptoFailedException e) {
						throw new Error("Couldn't decrypt data. Re-do key?");
					}
				} else {
				/* NDEF message was just to get us foregrounded -- try to decrypt using applet. */
					decryptUsingApplet(intent, dbinfo);
				}
			}
			return dbinfo;
		} else if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
			decryptUsingApplet(intent, dbinfo);
			return dbinfo;
		} else {
			/* Not a compatible intent */
			return null;
		}
	}

	static boolean startKeepassActivity(Context ctx, DatabaseInfo dbinfo)
	{
		KeePassApp app = KeePassApps.get().forId(dbinfo.getKeepassAppId());

		Intent intent = app.getIntent(ctx, dbinfo);

		ctx.startActivity(intent);

		return true;
	}
}
