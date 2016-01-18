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

import me.spadival.podmode.R;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;

public class SettingsFragment extends PreferenceFragment implements
		OnPreferenceChangeListener {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preferences);

		PackageManager pm = getActivity().getPackageManager();
		Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);

		List<ResolveInfo> mAppsInfo = pm
				.queryBroadcastReceivers(mediaIntent, 0);
		ListPreference simpleAppListPref = (ListPreference) findPreference("selectapp");
		ListPreference advancedAppListPref = (ListPreference) findPreference("selectadvancedapp");

		ListPreference baudRatePref = (ListPreference) findPreference("baud_rate");

		simpleAppListPref.setOnPreferenceChangeListener(this);
		advancedAppListPref.setOnPreferenceChangeListener(this);

		baudRatePref.setOnPreferenceChangeListener(this);
		baudRatePref.setTitle(baudRatePref.getEntry());

		CharSequence[] mEntries;
		CharSequence[] mEntryValues;

		CharSequence[] mAdvEntries;
		CharSequence[] mAdvEntryValues;

		if (mAppsInfo.size() > 0) {

			mEntries = new CharSequence[mAppsInfo.size()];
			mEntryValues = new CharSequence[mAppsInfo.size()];

			mAdvEntries = new CharSequence[mAppsInfo.size() + 1];
			mAdvEntryValues = new CharSequence[mAppsInfo.size() + 1];

			mAdvEntries[0] = "PodMode";
			mAdvEntryValues[0] = "me.spadival.podmode";

			int i = 0;
			for (ResolveInfo info : mAppsInfo) {
				mEntries[i] = info.activityInfo.applicationInfo.loadLabel(pm);
				mEntryValues[i] = (String) info.activityInfo.packageName;
				mAdvEntries[i + 1] = mEntries[i];
				mAdvEntryValues[i + 1] = mEntryValues[i];
				i++;
			}

			simpleAppListPref.setSelectable(true);
			simpleAppListPref.setEntries(mEntries);
			simpleAppListPref.setEntryValues(mEntryValues);

			advancedAppListPref.setSelectable(true);
			advancedAppListPref.setEntries(mAdvEntries);
			advancedAppListPref.setEntryValues(mAdvEntryValues);

			boolean simpleAppEntryFound = false;

			String simpleAppName = (String) simpleAppListPref.getEntry();

			if (simpleAppName != null) {
				for (i = 0; i < mEntries.length; i++) {
					if (simpleAppName.equals(mEntries[i])) {
						simpleAppEntryFound = true;
					}
				}
			}

			if (!simpleAppEntryFound)
				simpleAppListPref.setValue((String) mEntryValues[0]);

			simpleAppListPref.setTitle(simpleAppListPref.getEntry());
			try {
				simpleAppListPref.setIcon(pm
						.getApplicationIcon(simpleAppListPref.getValue()));
			} catch (NameNotFoundException e) {
				e.printStackTrace();
			}

			boolean advancedAppEntryFound = false;
			String advancedAppName = (String) advancedAppListPref.getEntry();

			if (advancedAppName != null) {
				for (i = 0; i < mAdvEntries.length; i++) {
					if (advancedAppName.equals(mAdvEntries[i])) {
						advancedAppEntryFound = true;
					}
				}
			}

			if (!advancedAppEntryFound)
				advancedAppListPref.setValue((String) mAdvEntryValues[0]);

			advancedAppListPref.setTitle(advancedAppListPref.getEntry());
			try {
				advancedAppListPref.setIcon(pm
						.getApplicationIcon(advancedAppListPref.getValue()));
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} else {
			simpleAppListPref.setTitle(R.string.no_media_player);
			simpleAppListPref.setEntries(null);
			simpleAppListPref.setEntryValues(null);
			simpleAppListPref.setEnabled(false);

			mAdvEntries = new CharSequence[1];
			mAdvEntryValues = new CharSequence[1];

			mAdvEntries[0] = "PodMode";
			mAdvEntryValues[0] = "me.spadival.podmode";

			advancedAppListPref.setTitle(mAdvEntries[0]);
			advancedAppListPref.setEntries(mAdvEntries);
			advancedAppListPref.setEntryValues(mAdvEntryValues);
			advancedAppListPref.setEnabled(false);
		}

	}

	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference.getKey().equals("selectapp")) {
			ListPreference mAppPref = (ListPreference) preference;
			CharSequence[] mEntries = mAppPref.getEntries();
			mAppPref.setTitle(mEntries[mAppPref
					.findIndexOfValue((String) newValue)]);

			PackageManager pm = getActivity().getPackageManager();

			try {
				mAppPref.setIcon(pm.getApplicationIcon((String) newValue));
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		
		if (preference.getKey().equals("selectadvancedapp")) {
			ListPreference mAdvancedAppPref = (ListPreference) preference;
			CharSequence[] mEntries = mAdvancedAppPref.getEntries();
			mAdvancedAppPref.setTitle(mEntries[mAdvancedAppPref
					.findIndexOfValue((String) newValue)]);

			PackageManager pm = getActivity().getPackageManager();

			try {
				mAdvancedAppPref.setIcon(pm.getApplicationIcon((String) newValue));
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		
		if (preference.getKey().equals("baud_rate")) {
			ListPreference baudRatePref = (ListPreference) preference;
			CharSequence[] mEntries = baudRatePref.getEntries();
			baudRatePref.setTitle(mEntries[baudRatePref
					.findIndexOfValue((String) newValue)]);
		}
		
		return true;

	}

}
