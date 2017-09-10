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
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import net.lardcave.keepassnfc.keepassapp.KeePassApp;
import net.lardcave.keepassnfc.keepassapp.KeePassApps;
import net.lardcave.keepassnfc.nfccomms.KPApplet;
import net.lardcave.keepassnfc.nfccomms.KPNdef;

import java.io.IOException;

public class ReadActivity extends Activity {
	private static final String LOG_TAG = "KPNFC Read";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		KPNdef ndef = null;
		Intent intent = getIntent();
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
			ndef = new KPNdef(getIntent());
		}

		if (ndef != null && ndef.readWasSuccessful()) {
			DatabaseInfo dbinfo = DatabaseInfo.deserialise(this);

			if(dbinfo == null) {
				Toast.makeText(this, "No KPNFC database set up.", Toast.LENGTH_SHORT).show();
				return;
			}

			byte[] secretKey = ndef.getSecretKey();

			if(secretKey != null)  {
				/* NDEF message contained the decryption key -- don't use applet. */
				try {
					dbinfo.decrypt_password(secretKey);
				} catch (CryptoFailedException e) {
					Toast.makeText(this, "Couldn't decrypt data. Re-do key?", Toast.LENGTH_SHORT).show();
					dbinfo = null;
				}
			} else {
				/* NDEF message contains no secrets -- decrypt using applet stored on card. */
				KPApplet applet = new KPApplet();
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

			if(dbinfo != null) {
				startKeepassActivity(dbinfo);
			}

			finish();
		}
	}

	private boolean startKeepassActivity(DatabaseInfo dbinfo)
	{
		KeePassApp app = KeePassApps.get().forId(dbinfo.getKeepassAppId());

		Intent intent = app.getIntent(this, dbinfo);

		startActivity(intent);

		return true;
	}

}
