package net.lardcave.keepassnfc.keepassapp;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import net.lardcave.keepassnfc.DatabaseInfo;

public class KeePassDroid implements KeePassApp {
	public static final String PACKAGE_NAME = "com.android.keepass";
	private static final String NAME = "KeePassDroid";
	private static final ComponentName KEEPASSDROID_COMPONENT_NAME = new ComponentName(
			PACKAGE_NAME, "com.keepassdroid.PasswordActivity");

	@Override
	public String getPackageName() {
		return PACKAGE_NAME;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getId() {
		// In case we need to support multiple ways to launch the same app.
		return PACKAGE_NAME + "_0";
	}

	@Override
	public Intent getIntent(Context ctx, DatabaseInfo dbinfo) {
		Intent intent = new Intent();

		intent.setComponent(KEEPASSDROID_COMPONENT_NAME);
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
		intent.putExtra("launchImmediately", dbinfo.config != DatabaseInfo.CONFIG_PASSWORD_ASK);

		return intent;
	}
}
