package net.lardcave.keepassnfc.keepassapp;

import android.content.Context;
import android.content.Intent;

import net.lardcave.keepassnfc.DatabaseInfo;

public interface KeePassApp {
	String getPackageName();
	String getName();
	String getId();
	Intent getIntent(Context ctx, DatabaseInfo info);
}
