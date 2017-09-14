package net.lardcave.keepassnfc.keepassapp;

import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

public class KeePassApps {
	private static KeePassApps singleton;
	private List<KeePassApp> apps;
	private static KeePassDroid keePassDroid = new KeePassDroid();

	private KeePassApps() {
		apps = new ArrayList<>();
		apps.add(keePassDroid);
		apps.add(new Keepass2Android());
	}

	public static KeePassApps get() {
		return singleton;
	}

	public static KeePassApp getDefaultApp() { return keePassDroid; }

	public List<KeePassApp> getAvailableApps(PackageManager pm) {
		ArrayList<KeePassApp> availableApps = new ArrayList<>();

		for(KeePassApp app: apps) {
			if(isPackageUsable(pm, app.getPackageName())) {
				availableApps.add(app);
			}
		}

		return availableApps;
	}

	public KeePassApp forId(String id) {
		for(KeePassApp app: apps) {
			if(app.getId().equals(id)) {
				return app;
			}
		}
		return null;
	}

	private boolean isPackageUsable(PackageManager pm, String packagename) {
		try {
			return pm.getApplicationInfo(packagename, 0).enabled;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	static {
		singleton = new KeePassApps();
	}

}
