/* Copyright (C) 2016  Shailendra Padival

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/> */

package me.spadival.podmode;


import java.util.List;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RemoteViews;
import android.widget.TextView;

@SuppressLint("UseSparseArrays")
public class PodNotifyService extends AccessibilityService {

	final static String THIS_PACKAGE = "me.spadival.podmode";
	final static String ANDROID_PACKAGE = "android";

	final static String NOTIFICATION_CLASS = "android.app.Notification";
	final static String SYSTEMUI_PACKAGE = "com.android.systemui";
	final static String GMAPS_PACKAGE = "com.google.android.apps.maps";
	final static String GNOW_PACKAGE = "com.google.android.googlequicksearchbox";
	final static String GMAIL_PACKAGE = "com.google.android.gm";
	final static String MSGS_PACKAGE = "com.android.mms";
	final static String PANDORA_PACKAGE = "com.pandora.android";
	final static String FMRADIO_PACKAGE = "com.sec.android.app.fm";


	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {

		String notifyPackage = (String) event.getPackageName();

		if (!event.getClassName().equals(NOTIFICATION_CLASS))
			return;

		if (notifyPackage.equals(SYSTEMUI_PACKAGE)
				|| notifyPackage.equals(THIS_PACKAGE)
				|| notifyPackage.equals(ANDROID_PACKAGE))
			return;

		PackageManager pm = getPackageManager();

		String notifyAppName = null;

		try {
			notifyAppName = (String) pm.getApplicationLabel(pm
					.getApplicationInfo(notifyPackage, 0));
		} catch (NameNotFoundException e1) {
			e1.printStackTrace();
		}

		if (notifyAppName == null)
			return;

		if (notifyPackage.equals(GMAPS_PACKAGE))
			notifyAppName = getString(R.string.nav_appname);

		if (notifyPackage.equals(GNOW_PACKAGE))
			notifyAppName = "Google Now";

		List<CharSequence> textList = event.getText();

		String notifyText = "";

		if (textList.size() > 0)
			notifyText = textList.get(0).toString();

		if (notifyText.equals("") || notifyPackage.equals(GMAIL_PACKAGE)) {
			Notification eventNotification = (Notification) event
					.getParcelableData();

			RemoteViews notifyView = eventNotification.contentView;
			LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			ViewGroup localView = null;
			try {
				localView = (ViewGroup) inflater.inflate(
						notifyView.getLayoutId(), null);
			} catch (Exception e) {
		//		e.printStackTrace();
				return;
			}

			try {

				notifyView.reapply(getApplicationContext(), localView);
			} catch (NotFoundException e) {
	//			e.printStackTrace();
			}
			


			View tv = localView.findViewById(android.R.id.title);
			if (tv != null && tv instanceof TextView) {
				if (notifyPackage.equals(GNOW_PACKAGE)
						|| notifyPackage.equals(PANDORA_PACKAGE))
					notifyText = ((TextView) tv).getText().toString();
				else
					notifyAppName += ": "
							+ ((TextView) tv).getText().toString();
			}

			if (!notifyPackage.equals(GNOW_PACKAGE)) {

				tv = localView.findViewById(16908358);
				if (tv != null && tv instanceof TextView)
					if (notifyPackage.equals(PANDORA_PACKAGE))
						notifyAppName += ": "
								+ ((TextView) tv).getText().toString();
					else
						notifyText = (String) ((TextView) tv).getText()
								.toString();
			}

			if (notifyPackage.equals(GMAIL_PACKAGE)) {
				tv = localView.findViewById(android.R.id.text2);
				if (tv != null && tv instanceof TextView)
					notifyText = (String) ((TextView) tv).getText().toString();
			}
		}


		Intent localIntent = new Intent(PodModeService.NOTIFYACTION);
		localIntent.putExtra("package", notifyPackage);
		localIntent.putExtra("appname", notifyAppName);
		localIntent.putExtra("text", notifyText);

		LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);

	}

	@Override
	public void onInterrupt() {
		// TODO Auto-generated method stub.


	}
}
