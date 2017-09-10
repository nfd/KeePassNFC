package net.lardcave.keepassnfc.keepassapp;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import net.lardcave.keepassnfc.DatabaseInfo;

public class Keepass2Android implements KeePassApp {
	public static final String PACKAGE_NAME = "keepass2android.keepass2android";
	private static final String NAME = "Keepass2Android";
	private static final String ACTIVITY_SUFFIX = "PasswordActivity";


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
		return PACKAGE_NAME + "_0";
	}

	@Override
	public Intent getIntent(Context ctx, DatabaseInfo dbinfo) {
		Intent intent = new Intent();

		ComponentName componentName = findComponentName(ctx);
		if(componentName == null)
			return null;

		intent.setComponent(componentName);
		intent.setAction("kp2a.action.PasswordActivity");

		intent.putExtra("fileName", dbinfo.database.toString());

		// Must also setData so URI permission grant works
		intent.setData(dbinfo.database);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

		if(dbinfo.keyfile != null) {
			intent.putExtra("keyFile", dbinfo.keyfile.toString());

			// Also use ClipData in order to pass permissions for multiple content:// URIs.
			// See https://developer.android.com/reference/android/content/Intent.html#setClipData%28android.content.ClipData%29
			ClipData.Item item = new ClipData.Item(dbinfo.keyfile);
			ClipData clipData = new ClipData("keyFile", new String[] {ClipDescription.MIMETYPE_TEXT_URILIST}, item);
			intent.setClipData(clipData);

		}

		intent.putExtra("password", dbinfo.password);
		intent.putExtra("launchImmediately", dbinfo.config != DatabaseInfo.CONFIG_PASSWORD_ASK);

		return intent;
	}

	static ComponentName findComponentName(Context ctx)
	{
		// Workaround for https://developer.xamarin.com/releases/android/xamarin.android_5/xamarin.android_5.1/#Android_Callable_Wrapper_Naming
		try {
			PackageInfo pi = ctx.getPackageManager().getPackageInfo(PACKAGE_NAME, PackageManager.GET_ACTIVITIES);

			for(ActivityInfo activity: pi.activities) {
				if(activity.name.endsWith(ACTIVITY_SUFFIX)) {
					return new ComponentName(PACKAGE_NAME, activity.name);
				}
			}

		} catch (PackageManager.NameNotFoundException e) {
			// it's not present.
		}
		return null;
	}

}
