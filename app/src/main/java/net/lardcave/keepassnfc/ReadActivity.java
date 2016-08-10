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

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;

public class ReadActivity extends Activity {
	private static final String TAG = "KPNFC Read";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		byte[] payload = null;

		Log.d(TAG, "Read activity start");
		
		Intent intent = getIntent();
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
	        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
	        if (rawMsgs != null) {
	            NdefMessage [] msgs = new NdefMessage[rawMsgs.length];
	            for (int j = 0; j < rawMsgs.length; j++) {
	                msgs[j] = (NdefMessage) rawMsgs[j];
	                NdefRecord record = msgs[j].getRecords()[0];
	                if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA)
	                {
	                	String mimetype = record.toMimeType();
	                	if (mimetype.equals(Settings.nfc_mime_type)) {
		                	payload = record.getPayload();
	                	}
	                }
	            }
	        }
		}

		if (payload != null && payload.length > 0) {
			DatabaseInfo dbinfo = DatabaseInfo.deserialise(this);

			if(dbinfo == null) {
				Toast.makeText(this, "No KPNFC database set up.", Toast.LENGTH_SHORT).show();
				return;
			}

			switch(payload[0]) {
				case Settings.KEY_TYPE_RAW: {

					byte[] decryption_key = Arrays.copyOfRange(payload, 1, payload.length);
					try {
						dbinfo.decrypt_password(decryption_key);
					} catch (CryptoFailedException e) {
						Toast.makeText(this, "Couldn't decrypt data. Re-do key?", Toast.LENGTH_SHORT).show();
						dbinfo = null;
					}
					break;
				}
				case Settings.KEY_TYPE_APP: {

					KPNFCApplet applet = new KPNFCApplet();
					byte[] decrypted_bytes = null;
					try {
						decrypted_bytes = applet.decrypt(intent, dbinfo.encrypted_password);
					} catch (IOException e) {
						Toast.makeText(this, "Card communication failed.", Toast.LENGTH_SHORT).show();
						dbinfo = null;
					}

					if(decrypted_bytes != null) {
						dbinfo.set_decrypted_password(decrypted_bytes);
					}
				}
			}

			if(dbinfo != null) {
				startKeepassActivity(dbinfo);
			}

			finish();
		}
	}

	private boolean startKeepassActivity(DatabaseInfo dbinfo)
	{
		Intent intent = new Intent();
		
		intent.setComponent(new ComponentName("com.android.keepass", "com.keepassdroid.PasswordActivity"));
		intent.setAction(Intent.ACTION_VIEW);

		intent.setData(dbinfo.database);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

		if(dbinfo.keyfile != null) {
			// Use ClipData in order to pass permissions for multiple content:// URIs.
			// See https://developer.android.com/reference/android/content/Intent.html#setClipData%28android.content.ClipData%29
			ClipData.Item item = new ClipData.Item(dbinfo.keyfile);
			ClipData clipData = new ClipData("keyFile", new String[] {ClipDescription.MIMETYPE_TEXT_URILIST}, item);
			intent.setClipData(clipData);
		}

		intent.putExtra("password", dbinfo.password);
		intent.putExtra("launchImmediately", dbinfo.config != Settings.CONFIG_PASSWORD_ASK);

		startActivity(intent);
		return true;
	}

}
