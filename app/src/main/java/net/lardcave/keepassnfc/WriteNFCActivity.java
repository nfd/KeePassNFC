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

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import net.lardcave.keepassnfc.nfccomms.KPApplet;
import net.lardcave.keepassnfc.nfccomms.KPNdef;

public class WriteNFCActivity extends Activity {
    private static final String LOG_TAG = "WriteNFCActivity";

	private byte[] randomBytes; // Key

    protected void onCreate(Bundle sis) {
        super.onCreate(sis);

		randomBytes = getIntent().getExtras().getByteArray("randomBytes");

		if(randomBytes == null) {
            throw new RuntimeException("No randombytes supplied");
        }

		if(randomBytes.length != Settings.key_length) {
			throw new RuntimeException("Unexpected key length " + randomBytes.length);
		}

		setContentView(R.layout.activity_write_nfc);

        setResult(0);

        Button b = (Button) findViewById(R.id.cancel_nfc_write_button);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View self) {
                NfcReadActions.nfc_disable(WriteNFCActivity.this);
                finish();
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        NfcReadActions.nfc_enable(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        NfcReadActions.nfc_disable(this);
    }

    @Override
    public void onNewIntent(Intent intent)
    {
	    boolean writeNdefToSmartcard = ((CheckBox)findViewById(R.id.cbWriteNDEF)).isChecked();

        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
				|| NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {

			// Attempt to access the card first as a smartcard and then as an NDEF card.

			boolean appletWritten = false;
			try {
				KPApplet applet = new KPApplet();
				appletWritten = applet.write(intent, randomBytes, writeNdefToSmartcard);
			} catch (IOException e) {
				e.printStackTrace();
			}

			boolean ndefWritten = false;
			if(!appletWritten) {
				// try NDEF instead.
				Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
				KPNdef ndef = new KPNdef(randomBytes);
				ndefWritten = ndef.write(tag);
			}

			Intent resultIntent = new Intent();
			resultIntent.putExtra("randomBytes", randomBytes);

			setResult(appletWritten || ndefWritten ? 1 : 0, resultIntent);
            finish();
        }

		if(action.equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
			System.out.println("Tag only...");
		}
    }

}
