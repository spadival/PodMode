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

import me.spadival.podmode.R;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

public class MainActivity extends Activity {

	public static final String BANNER = "me.spadival.podmode.BANNER";
	public static final String CLOSE = "me.spadival.podmode.CLOSE";


	private TextView mProcessText;
	private TextView mSongText;
	private ImageView mAppIcon;
	boolean startViaUSB = false;
	private boolean mStartViaUSB;

	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)
					|| UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				finish();
			}
		}
	};

	BroadcastReceiver mPlayReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(BANNER)) {
				String processName = intent.getStringExtra("banner");
				String songName = intent.getStringExtra("songname");
				String albumArtURI = intent.getStringExtra("albumarturi");

				if (processName != null)
					mProcessText.setText(processName);

				if (songName != null)
					mSongText.setText(songName);

				if (albumArtURI != null)
					if (!albumArtURI.equals("")) {
						try {
							Uri uri = Uri.parse(albumArtURI);
							mAppIcon.setImageURI(uri);
							mAppIcon.setScaleType(ScaleType.FIT_CENTER);
							mAppIcon.setVisibility(View.VISIBLE);

						} catch (Exception e) {
							mAppIcon.setVisibility(View.INVISIBLE);
						}
					} else
						mAppIcon.setVisibility(View.INVISIBLE);
			}
			if (action.equals(CLOSE)) {
				finish();
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {


		String action = this.getIntent().getAction();
		if (action == null)
			action = "";


		if (!action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)
				&& !action.equals(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)) {

			this.setTheme(android.R.style.Theme_Holo);
			super.onCreate(savedInstanceState);

			setContentView(R.layout.activity_main);

			IntentFilter usbFilter = new IntentFilter();
			usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
			usbFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);

			registerReceiver(mUsbReceiver, usbFilter);

			LocalBroadcastManager bManager = LocalBroadcastManager
					.getInstance(this);
			IntentFilter playFilter = new IntentFilter();
			playFilter.addAction(BANNER);
			playFilter.addAction(CLOSE);
			bManager.registerReceiver(mPlayReceiver, playFilter);

			mSongText = (TextView) findViewById(R.id.tvPodSongName);
			mProcessText = (TextView) findViewById(R.id.tvPodProcess);
			mAppIcon = (ImageView) findViewById(R.id.ivAppIcon);

			Intent serviceIntent = new Intent(this,PodModeService.class);
			startService(serviceIntent);

		} else {

			super.onCreate(savedInstanceState);

			mStartViaUSB = true;

			Intent serviceIntent = new Intent(
					"me.spadival.podmode.PodModeService");

			startService(serviceIntent);
			finish(); // If started via USB insert, this activity is done
						// here -
						// don't want the view coming in the way..
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {

		Intent intent = new Intent();
		if (item.getTitle().equals(getString(R.string.menu_notices)))
			intent.setClass(MainActivity.this, NoticesActivity.class);

		else
			intent.setClass(MainActivity.this, SettingsActivity.class);
		startActivityForResult(intent, 0);

		return true;
	}

	@Override
	protected void onDestroy() {
		if (!mStartViaUSB) {
			unregisterReceiver(mUsbReceiver);
			LocalBroadcastManager.getInstance(this).unregisterReceiver(
					mPlayReceiver);
		}

		super.onDestroy();
	}

}
