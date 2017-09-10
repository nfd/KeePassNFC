package net.lardcave.keepassnfc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;

import net.lardcave.keepassnfc.keepassapp.KeePassDroid;
import net.lardcave.keepassnfc.keepassapp.Keepass2Android;

public class NoKeepassActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_no_keepass);

		findViewById(R.id.bInstallKeePassDroid).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + KeePassDroid.PACKAGE_NAME));
				startActivity(intent);
			}
		});

		findViewById(R.id.bInstallKeepass2Android).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Keepass2Android.PACKAGE_NAME));
				startActivity(intent);
			}
		});
	}

}
