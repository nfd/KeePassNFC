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

import java.io.File;
import java.security.SecureRandom;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;


/* Probably want this to have foreground NFC-everything, so that people can scan a fob and then press the button?
 * Does that even work?
 */
public class PrepareNewTagActivity extends Activity {

	private static final int PASSWORD_NO = 0;
	private static final int PASSWORD_ASK = 1;
	private static final int PASSWORD_YES = 2;
	private static final int KEYFILE_NO = 0;
	private static final int KEYFILE_YES = 1;
	private static final int REQUEST_KEYFILE = 0;
	private static final int REQUEST_DATABASE = 1;
    private static final int REQUEST_NFC_WRITE = 2;
	private Uri keyfile = null;
	private Uri database = null;
	private byte[] random_bytes = new byte[Settings.key_length];
	public static NdefMessage nfc_payload;
	
	private int keyfile_option = KEYFILE_NO;
	private int password_option = PASSWORD_NO;
	
	@Override
	protected void onCreate(Bundle sis) {
		super.onCreate(sis);
		setContentView(R.layout.activity_write);
		
		if (sis != null) {
			password_option = sis.getInt("password_option");
			keyfile_option  = sis.getInt("keyfile_option");
			if (sis.getString("keyfile").compareTo("") != 0)
				keyfile = Uri.parse(sis.getString("keyfile"));
			else
				keyfile = null;
		}
		
		initialiseView();
	}

	@Override
	protected void onSaveInstanceState(Bundle sis)
	{
	    super.onSaveInstanceState(sis);
	    if (keyfile == null)
	    	sis.putString("keyfile", "");
	    else
	    	sis.putString("keyfile", keyfile.toString());
	    sis.putInt("keyfile_option", keyfile_option);
	    sis.putInt("password_option", password_option);

		Log.d("KPNFC", "Saved instance state");
	}

	private void openPicker(int result) {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*"); // https://code.google.com/p/android/issues/detail?id=63550
		startActivityForResult(intent, result);
	}
	
	private void initialiseView()
	{
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		
		updateRadioViews();
		updateNonRadioViews();
		
		RadioButton rb;
		
		rb = (RadioButton) findViewById(R.id.keyfile_no);
		rb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked)
					keyfile_option = KEYFILE_NO;
				else
					keyfile_option = KEYFILE_YES;
				updateNonRadioViews();
			}});

		rb = (RadioButton) findViewById(R.id.password_yes);
		rb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked)
					password_option = PASSWORD_YES;
				else
					password_option = PASSWORD_NO;
				updateNonRadioViews();
			}});
		
		Button b = (Button) findViewById(R.id.write_nfc);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View self) {
				create_random_bytes();
                self.setEnabled(false);
                Intent intent = new Intent(getApplicationContext(), WriteNFCActivity.class);
                startActivityForResult(intent, REQUEST_NFC_WRITE);
			}
		});
		
		ImageButton ib = (ImageButton) findViewById(R.id.choose_keyfile);
		ib.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				keyfile_option = KEYFILE_YES;
				setRadio(R.id.keyfile_yes, true);

				openPicker(REQUEST_KEYFILE);
			}
			
		});
		
		ib = (ImageButton) findViewById(R.id.choose_database);
		ib.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				openPicker(REQUEST_DATABASE);
			}
		});
	}

	// Stuff came back from file chooser

	private File uriToFilename(Uri uri) {
		String filename = HideousUriToFileHack.getPath(getApplicationContext(), uri);

		return new File(filename);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    switch (requestCode) {
	    case REQUEST_KEYFILE:  
	        if (resultCode == RESULT_OK) {  
	            // The URI of the selected file 
	            keyfile = data.getData();
	            updateNonRadioViews();
	        } else {
				System.err.println("REQUEST_KEYFILE result code " + resultCode);
			}
	        break;
	    case REQUEST_DATABASE:
	    	if (resultCode == RESULT_OK) {
	    		database = data.getData();
	    		updateNonRadioViews();
	    	} else {
				System.err.println("REQUEST_DATABASE result code " + resultCode);
			}
	    	break;
        case REQUEST_NFC_WRITE:
            // Re-enable NFC writing.
            Button nfc_write = (Button) findViewById(R.id.write_nfc);
            nfc_write.setEnabled(true);

            if (resultCode == 1) {
                if (encrypt_and_store()) {
                    // Job well done! Let's have some toast.
                    Toast.makeText(getApplicationContext(), "Tag written successfully!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Error writing to application database!", Toast.LENGTH_SHORT).show();
                }
            } else {
                // can't think of a good toast analogy for fail
                Toast.makeText(getApplicationContext(), "Couldn't write tag. :(", Toast.LENGTH_SHORT).show();
            }
        }
	}

	
	private void create_random_bytes()
	{
		SecureRandom rng = new SecureRandom();		
		rng.nextBytes(random_bytes);

		// Create the NFC version of this data		
		NdefRecord ndef_records = NdefRecord.createMime(Settings.nfc_mime_type, random_bytes);
		nfc_payload = new NdefMessage(ndef_records);
	}
	
	private boolean encrypt_and_store()
	{	
		DatabaseInfo dbinfo;
		int config;
		String keyfile_uri_string;
		String password;
		
		if (database == null) {
			Toast.makeText(getApplicationContext(), "Please select a database first", Toast.LENGTH_SHORT).show();
			return false;
		}
		
		if (password_option == PASSWORD_ASK)
			config = Settings.CONFIG_PASSWORD_ASK;
		else
			config = Settings.CONFIG_NOTHING;
		
		// Policy: no password is stored as null password (bit silly?)
		if (password_option == PASSWORD_NO)
			password = "";
		else {
			EditText et_password = (EditText) findViewById(R.id.password);
			password = et_password.getText().toString();
		}
		
		dbinfo = new DatabaseInfo(database, keyfile_option == KEYFILE_NO? null: keyfile, password, config);
		
		try {
			return dbinfo.serialise(this, random_bytes);
		} catch (CryptoFailedException e) {
			Toast.makeText(getApplicationContext(), "Couldn't encrypt data :(", Toast.LENGTH_SHORT).show();
			return false;
		}
	}

	private void updateRadioViews()
	{
		setRadio(R.id.keyfile_no, keyfile_option == KEYFILE_NO);
		setRadio(R.id.keyfile_yes, keyfile_option == KEYFILE_YES);
		setRadio(R.id.password_no, password_option == PASSWORD_NO);
		setRadio(R.id.password_ask, password_option == PASSWORD_ASK);
		setRadio(R.id.password_yes, password_option == PASSWORD_YES);		
	}
	
	private void updateNonRadioViews()
	{
		EditText et_password = (EditText) findViewById(R.id.password);
		et_password.setEnabled(password_option == PASSWORD_YES);		
		
		TextView tv_keyfile = (TextView) findViewById(R.id.keyfile_name);
		tv_keyfile.setEnabled(keyfile_option == KEYFILE_YES);
		if (keyfile != null)
			tv_keyfile.setText(keyfile.toString());
		else
			tv_keyfile.setText("...");
		
		TextView tv_database = (TextView) findViewById(R.id.database_name);
		if (database != null)
			tv_database.setText(database.toString());
		else
			tv_database.setText("...");
	}
	
	private void setRadio(int id, boolean checked)
	{
		RadioButton rb = (RadioButton) findViewById(id);
		rb.setChecked(checked);
	}

/*	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.write, menu);
		return true;
	}
*/

}
