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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

//import com.woodblockwithoutco.remotemetadataprovider.media.RemoteMetadataProvider;
//import com.woodblockwithoutco.remotemetadataprovider.media.listeners.OnMetadataChangeListener;

import me.spadival.podmode.FT311UARTInterface;

import me.spadival.podmode.MusicRetriever.Track;

import me.spadival.podmode.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

public class PodModeService extends Service implements Runnable,
		OnCompletionListener, OnPreparedListener, OnErrorListener,
		MusicFocusable, OnSharedPreferenceChangeListener,
		PrepareMusicRetrieverTask.MusicRetrieverPreparedListener {

	final static String TAG = "PodModeService";
	final static String PACKAGENAME = "me.spadival.podmode";
	final static String NOTIFYACTION = "me.spadival.podmode.NOTIFY";

	// Message types sent from the BluetoothChatService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BluetoothChatService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	private volatile Thread mMainThread;

	// The volume we set the media player to when we lose audio focus, but are
	// allowed to reduce
	// the volume instead of stopping playback.
	public static final float DUCK_VOLUME = 0.1f;

	// our media player
	MediaPlayer mPlayer = null;

	// our AudioFocusHelper object, if it's available (it's available on SDK
	// level >= 8)
	// If not available, this will be null. Always check for null before using!
	AudioFocusHelper mAudioFocusHelper = null;

	// indicates the state our service:
	enum State {
		Retrieving, // the MediaRetriever is retrieving music
		Stopped, // media player is stopped and not prepared to play
		Preparing, // media player is preparing...
		Playing, // playback active (media player ready!). (but the media player
					// may actually be
					// paused in this state if we don't have audio focus. But we
					// stay in this state
					// so that we know we have to resume playback once we get
					// focus back)
		Paused // playback paused (media player ready!)
	};

	private State mState = State.Retrieving;

	private int mNowPlaying = 0;
	private int mPrevPlaying = 0;
	private int mPollSpeed = 0;
	private int mChangedCounter = 0;
	private boolean mNotifyHack = false;

	private String mBanner = "";

	// if in Retrieving mode, this flag indicates whether we should start
	// playing immediately
	// when we are ready or not.
	boolean mStartPlayingAfterRetrieve = false;

	// if mStartPlayingAfterRetrieve is true, this variable indicates the URL
	// that we should
	// start playing when we are ready. If null, we should play a random song
	// from the device
	Uri mWhatToPlayAfterRetrieve = null;

	enum PauseReason {
		UserRequest, // paused by user request
		FocusLoss, // paused because of audio focus loss
	};

	// why did we pause? (only relevant if mState == State.Paused)
	PauseReason mPauseReason = PauseReason.UserRequest;

	// do we have audio focus?
	enum AudioFocus {
		NoFocusNoDuck, // we don't have audio focus, and can't duck
		NoFocusCanDuck, // we don't have focus, but can play at a low volume
						// ("ducking")
		Focused // we have full audio focus
	}

	AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;

	String mAppName = "";
	// title of the song we are currently playing
	String mSongTitle = "";
	String mAlbumArtUri = null;
	int mElapsedTime = 0;

	// whether the song we are playing is streaming from the network
	boolean mIsStreaming = false;

	// Wifi lock that we hold when streaming files from the internet, in order
	// to prevent the
	// device from shutting off the Wifi radio
	WifiLock mWifiLock;

	// The ID we use for the notification (the onscreen alert that appears at
	// the notification
	// area at the top of the screen as an icon -- and as text as well if the
	// user expands the
	// notification area).
	final int NOTIFICATION_ID = 1;

	// Our instance of our MusicRetriever, which handles scanning for media and
	// providing titles and URIs as we need.
	MusicRetriever mRetriever;

	AudioManager mAudioManager;
	NotificationManager mNotificationManager;

	Notification mNotification = null;

	final byte[] DEVTYPESIZE = { 0x00, 0x13, 0x01, 0x09 };
	final String WAKETAG = "PodModeService";

	enum deviceType {
		FT311D, FT232PL2303, MC05BT
	};

	private deviceType mDeviceType;
	private FTDriver mSerialHost;
	private FT311UARTInterface mSerialDevice;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothDevice mBluetoothDevice;
	private BluetoothSerialService mBTDevice = null;

	private int mSerialBaudRate = FTDriver.BAUD57600;
	private int mReadLen = 0;
	private byte[] mReadBuffer = new byte[4096];

	private boolean mPodRunning = false;
	private boolean mLaunchFirstTime = true;

	private WakeLock podWakeLock = null;

	enum podStat {
		WAITING, SIMPLEREMOTE, ADVANCEDREMOTE, ADVANCEDHACK, DISPLAYREMOTE
	};

	enum modeStat {
		Off, Songs, Albums
	}

	private byte mUpdateFlag = 0x00;

	private modeStat mPodRepeatMode = modeStat.Off;
	private modeStat mPodShuffleMode = modeStat.Off;

	private podStat mPodStatus = podStat.WAITING;

	private String mSimpleRemoteApp;
	private String mAdvancedRemoteApp;

	private String mAccessoryName = "-";
	private String mAccessoryMnf = "-";
	private String mAccessoryModel = "-";
	private boolean mHTTPsend = false;

	// Polling Timer Task run during Advanced Remote mode to send back elapsed
	// time and/or any change of song track.

	private void serialWrite(byte[] respBytes) {
		if (mDeviceType == deviceType.FT311D)
			mSerialDevice.SendData(respBytes.length, respBytes);
		else if (mDeviceType == deviceType.FT232PL2303)
			mSerialHost.write(respBytes);
		else if (mDeviceType == deviceType.MC05BT)
			mBTDevice.write(respBytes);
	}

	int[] mLen = new int[1];

	private int mSerialRead(byte[] readBuf) {

		if (mDeviceType == deviceType.FT311D) {
			mSerialDevice.ReadData(readBuf.length, readBuf, mLen);
			return mLen[0];
		} else if (mDeviceType == deviceType.FT232PL2303)
			return mSerialHost.read(readBuf);
		else if (mDeviceType == deviceType.MC05BT)
			return mBTDevice.read(readBuf);
		else
			return 0;

	}

	private Timer mMediaChangeTimer;

	private TimerTask mMediaChangeTask = new TimerTask() {
		public void run() {
			if (mPollSpeed != 0) {
				byte[] respBytes = new byte[] { (byte) 0xFF, 0x55, 0x08, 0x04,
						0x00, 0x27, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };

				int elapsedTime = 0;

				if (mPlayer != null) {
					if (mPlayer.isPlaying()) {
						elapsedTime = mPlayer.getCurrentPosition();
						if (mState == State.Playing
								|| mState == State.Preparing) {
							respBytes[6] = 0x04;
						}
					}
				} else {
					respBytes[6] = 0x04;
					mElapsedTime += 500;
					elapsedTime = mElapsedTime;
				}

				respBytes[7] = (byte) (elapsedTime >>> 24);
				respBytes[8] = (byte) (elapsedTime >>> 16);
				respBytes[9] = (byte) (elapsedTime >>> 8);
				respBytes[10] = (byte) elapsedTime;

				if (mChangedCounter > 0 && mChangedCounter++ >= mPollSpeed * 2) {
					mChangedCounter = 0;
					mUpdateFlag = 0x01;
					respBytes[6] = 0x01;
					if (mNowPlaying > 0) {
						respBytes[7] = (byte) ((mNowPlaying) >>> 24);
						respBytes[8] = (byte) ((mNowPlaying) >>> 16);
						respBytes[9] = (byte) ((mNowPlaying) >>> 8);
						respBytes[10] = (byte) mNowPlaying;
					}

				}

				short checkSum = 0;

				for (int i = 2; i < 11; i++)
					checkSum += (short) respBytes[i];

				checkSum = (short) (0x100 - checkSum);
				respBytes[11] = (byte) checkSum;
				serialWrite(respBytes);

			}
		}
	};

	private Thread mHttpThread = new Thread() {
		@Override
		public void run() {

			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(
					"http://www.spadival.me/podmode/android.php");
			try {
				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(
						6);
				nameValuePairs.add(new BasicNameValuePair("accessoryname",
						mAccessoryName));
				nameValuePairs.add(new BasicNameValuePair("accessorymanf",
						mAccessoryMnf));
				nameValuePairs.add(new BasicNameValuePair("accessorymodel",
						mAccessoryModel));
				nameValuePairs.add(new BasicNameValuePair("accessoryspeed",
						String.valueOf(mSerialBaudRate)));

				if (mPodStatus == podStat.SIMPLEREMOTE)
					nameValuePairs.add(new BasicNameValuePair("accessorymode",
							"Simple Remote"));
				if (mPodStatus == podStat.ADVANCEDHACK
						|| mPodStatus == podStat.ADVANCEDREMOTE)
					nameValuePairs.add(new BasicNameValuePair("accessorymode",
							"Advanced Remote"));
				else
					nameValuePairs.add(new BasicNameValuePair("accessorymode",
							"-"));

				nameValuePairs.add(new BasicNameValuePair("devicename",
						android.os.Build.MODEL));
				nameValuePairs.add(new BasicNameValuePair("devicemanf",
						android.os.Build.MANUFACTURER));
				httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
				httpclient.execute(httppost);

				SharedPreferences prefs = PreferenceManager
						.getDefaultSharedPreferences(PodModeService.this);

				SharedPreferences.Editor editor = prefs.edit();
				editor.putString("accessoryName", mAccessoryName);
				editor.putString("accessoryMnf", mAccessoryMnf);
				editor.putString("accessoryModel", mAccessoryModel);
				editor.commit();

			} catch (ClientProtocolException e) {
				e.printStackTrace();

			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	};

	private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)
					|| UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				mPodRunning = false;
				Toast.makeText(getApplicationContext(),
						"USB disconnected, Closing PodMode", Toast.LENGTH_LONG)
						.show();
				stopSelf();
			}
		}
	};

	// The Handler that gets information back from the BluetoothChatService
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				switch (msg.arg1) {
				case BluetoothSerialService.STATE_CONNECTED:
					if (!mPodRunning) {
						mDeviceType = deviceType.MC05BT;
						mPodRunning = true;
						mReadLen = 0;
						mBanner = getString(R.string.podmode_connected);
						localBroadcast(false);
						setUpAsForeground(getString(R.string.podmode_connected));
						mainloop();
					}
					break;
				case BluetoothSerialService.STATE_CONNECTING:
					break;
				case BluetoothSerialService.STATE_LISTEN:
					if (!mPodRunning) {
						mBanner = getString(R.string.bad_usb);
						setUpAsForeground(mBanner);
						localBroadcast(true);
						stopSelf();
					}
					break;
				case BluetoothSerialService.STATE_DISCONNECTED:
					if (mDeviceType == deviceType.MC05BT) {
						mPodRunning = false;
						Toast.makeText(getApplicationContext(),
								"Bluetooth disconnected, Closing PodMode",
								Toast.LENGTH_LONG).show();
						Intent localIntent = new Intent(MainActivity.CLOSE);
						LocalBroadcastManager.getInstance(PodModeService.this)
								.sendBroadcast(localIntent);
						stopSelf();
					}
					break;
				case BluetoothSerialService.STATE_NONE:
					break;
				}
				break;
			case MESSAGE_WRITE:
				break;
			case MESSAGE_READ:
				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				String mConnectedDeviceName = msg.getData().getString(
						DEVICE_NAME);
				Toast.makeText(getApplicationContext(),
						"Connected to " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}
	};

	private Timer mNotifyChangeTimer = null;

	private TimerTask mNotifyChangeTask = null;

	private BroadcastReceiver mNotifyReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(NOTIFYACTION)
					&& mPodStatus == podStat.ADVANCEDHACK) {
				String packageName = intent.getStringExtra("package");
				String appName = intent.getStringExtra("appname");
				String text = intent.getStringExtra("text");
				Log.d("PodMode", "NotifyReceiver " + text);

				if (packageName.equals(mAdvancedRemoteApp))
					return;

				mRetriever.changeApp(false, appName, text, 999);

				synchronized (this) {

					mChangedCounter = 1;
					mNotifyHack = true;

					if (mNotifyChangeTask != null)
						mNotifyChangeTask.cancel();

					mNotifyChangeTask = new TimerTask() {
						public void run() {
							mRetriever.changeApp(false, mAppName, mSongTitle,
									999);
							mChangedCounter = 1;
							mNotifyHack = true;
						}
					};

					mNotifyChangeTimer = new Timer();

					mNotifyChangeTimer.schedule(mNotifyChangeTask, 15000);

				}

			}
		}
	};

	// private RemoteMetadataProvider mProvider;

	/*
	 * public OnMetadataChangeListener mMetadataListner = new
	 * OnMetadataChangeListener() {
	 * 
	 * @Override public void onMetadataChanged(String artist, String title,
	 * String album, String albumArtist, long duration) {
	 * 
	 * if (title == null || album == null) return;
	 * 
	 * if (title.equals(mSongTitle)) return;
	 * 
	 * if (mPodStatus == podStat.ADVANCEDHACK) {
	 * 
	 * String appName = album; String text = title;
	 * 
	 * mRetriever.changeApp(false, appName, text, duration);
	 * 
	 * synchronized (this) { mSongTitle = text; mAppName = appName; mElapsedTime
	 * = 0;
	 * 
	 * mChangedCounter = 1; mNotifyHack = true; } } } };
	 */

	@Override
	public void onCreate() {

		IntentFilter usbFilter = new IntentFilter();
		usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		usbFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, usbFilter);
		// mProvider = RemoteMetadataProvider.getInstance(this);

		// mProvider.setOnMetadataChangeListener(mMetadataListner);
		// mProvider.acquireRemoteControls();
		LocalBroadcastManager bManager = LocalBroadcastManager
				.getInstance(this);
		IntentFilter notifyFilter = new IntentFilter();
		notifyFilter.addAction(NOTIFYACTION);
		bManager.registerReceiver(mNotifyReceiver, notifyFilter);

		mSerialHost = new FTDriver(
				(UsbManager) getSystemService(Context.USB_SERVICE));

		mSerialDevice = new FT311UARTInterface(this, null);

		// Get the local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// Get a set of currently paired devices
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
				.getBondedDevices();

		mBluetoothDevice = null;

	/*	// If there are paired devices, add each one to the ArrayAdapter
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) {
				if (device.getName().equals("PodModeBT"))
					mBluetoothDevice = device;
			}
		}

		if (mBluetoothDevice != null) {
			mBTDevice = new BluetoothSerialService(this, mHandler);
			mBTDevice.connect(mBluetoothDevice);
		} */

		// Create the Wifi lock (this does not acquire the lock, this just
		// creates it)
		mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
				.createWifiLock(WifiManager.WIFI_MODE_FULL, "mylock");

		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

		// Create the retriever and start an asynchronous task that will prepare
		// it.

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);

		mRetriever = new MusicRetriever(getContentResolver(),
				getApplicationContext(), true, prefs);

		// mRetriever.switchToMainPlaylist();

		prefs.registerOnSharedPreferenceChangeListener((OnSharedPreferenceChangeListener) this);

		mNowPlaying = prefs.getInt("nowplaying", 0);

		String mBaudrate = prefs.getString("baud_rate", "57600");

		if (mBaudrate.equals("57600"))
			mSerialBaudRate = FTDriver.BAUD57600;
		else if (mBaudrate.equals("38400"))
			mSerialBaudRate = FTDriver.BAUD38400;
		else if (mBaudrate.equals("19200"))
			mSerialBaudRate = FTDriver.BAUD19200;

		(new PrepareMusicRetrieverTask(mRetriever, this)).execute();

		// create the Audio Focus Helper, if the Audio Focus feature is
		// available (SDK 8 or above)
		if (android.os.Build.VERSION.SDK_INT >= 8)
			mAudioFocusHelper = new AudioFocusHelper(getApplicationContext(),
					this);
		else
			mAudioFocus = AudioFocus.Focused; // no focus feature, so we always
												// "have" audio focus

		boolean wakeLockPreferred = prefs.getBoolean("wakelock", false);

		if (podWakeLock == null && wakeLockPreferred) {
			PowerManager powerMgr = (PowerManager) getSystemService(Context.POWER_SERVICE);
			podWakeLock = powerMgr.newWakeLock(
					PowerManager.SCREEN_DIM_WAKE_LOCK, WAKETAG);
			podWakeLock.acquire();
		}

		PackageManager pxm = getPackageManager();
		Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);

		List<ResolveInfo> mAppsInfo = pxm.queryBroadcastReceivers(mediaIntent,
				0);

		mSimpleRemoteApp = prefs.getString("selectapp", null);
		mAdvancedRemoteApp = prefs.getString("selectadvancedapp", PACKAGENAME);

		// Make sure App preferences are still valid and Apps haven't been
		// uninstalled.

		if (mAppsInfo.size() > 0) {

			CharSequence[] entryValues = new CharSequence[mAppsInfo.size()];
			CharSequence[] advEntryValues = new CharSequence[mAppsInfo.size() + 1];

			advEntryValues[0] = PACKAGENAME;

			int i = 0;
			for (ResolveInfo info : mAppsInfo) {
				entryValues[i] = (String) info.activityInfo.packageName;
				advEntryValues[i + 1] = (String) info.activityInfo.packageName;

				i++;
			}

			boolean entryFound = false;

			if (mSimpleRemoteApp != null) {
				for (i = 0; i < entryValues.length; i++) {
					if (mSimpleRemoteApp.equals(entryValues[i])) {
						entryFound = true;
					}
				}
			}

			if (!entryFound) {
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString("selectapp", (String) entryValues[0]);
				editor.commit();
				mSimpleRemoteApp = (String) entryValues[0];
			}

			entryFound = false;

			if (mAdvancedRemoteApp != null) {
				for (i = 0; i < advEntryValues.length; i++) {
					if (mAdvancedRemoteApp.equals(advEntryValues[i])) {
						entryFound = true;
					}
				}
			}

			if (!entryFound) {
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString("selectadvancedapp",
						(String) advEntryValues[0]);
				editor.commit();
				mAdvancedRemoteApp = (String) advEntryValues[0];

			}
		} else {
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("selectadvancedapp", PACKAGENAME);
			editor.commit();
			mAdvancedRemoteApp = PACKAGENAME;
		}

		mAccessoryName = prefs.getString("accessoryName", null);
		mAccessoryMnf = prefs.getString("accessoryMnf", null);
		mAccessoryModel = prefs.getString("accessoryModel", null);

	}

	@Override
	public void onMusicRetrieverPrepared() {
		// Done retrieving!
		mState = State.Stopped;

		if (mSerialHost.begin(mSerialBaudRate)) {
		//	mBTDevice.stop();
			mDeviceType = deviceType.FT232PL2303;
			mPodRunning = true;
		} else {
			if (mSerialDevice.ResumeAccessory() == 0) {
		//		mBTDevice.stop();
				mDeviceType = deviceType.FT311D;
				mSerialDevice.SetConfig(mSerialBaudRate, (byte) 0x8,
						(byte) 0x1, (byte) 0x0, (byte) 0x0);
				mPodRunning = true;
			} else if (mBTDevice != null) {
				if (mBTDevice.getState() == BluetoothSerialService.STATE_CONNECTED) {
					mDeviceType = deviceType.MC05BT;
					mPodRunning = true;
				} else {
					mBanner = getString(R.string.podmode_connecting);
					setUpAsForeground(mBanner);
					localBroadcast(true);
				}
			} else {
				mBanner = getString(R.string.bad_usb);
				setUpAsForeground(mBanner);
				localBroadcast(true);
				stopSelf();
			}
		}

		if (mPodRunning) {
			mReadLen = 0;
			mBanner = getString(R.string.podmode_connected);
			localBroadcast(false);
			setUpAsForeground(getString(R.string.podmode_connected));

			mainloop();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		localBroadcast(false);
		return START_STICKY;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		if (key.equals("selectapp")) {
			mSimpleRemoteApp = sharedPreferences.getString("selectapp", null);
		}

		if (key.equals("selectadvancedapp")) {
			mAdvancedRemoteApp = sharedPreferences.getString(
					"selectadvancedapp", null);
		}

		mLaunchFirstTime = true;
	}

	private void mainloop() {
		if (mMainThread == null) {
			mMainThread = new Thread(this);
			mMainThread.start(); // This starts run() below
		}
	}

	public void broadcastMediaButtons(int keyCode, String app) {
		long eventtime = SystemClock.uptimeMillis();

		Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
		KeyEvent downEvent = new KeyEvent(eventtime, eventtime,
				KeyEvent.ACTION_DOWN, keyCode, 0);
		downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
		if (app != null)
			downIntent.setPackage(app);

		sendOrderedBroadcast(downIntent, null);

		eventtime = SystemClock.uptimeMillis();

		Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
		KeyEvent upEvent = new KeyEvent(eventtime, eventtime,
				KeyEvent.ACTION_UP, keyCode, 0);
		upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
		if (app != null)
			upIntent.setPackage(app);

		sendOrderedBroadcast(upIntent, null);

	}

	public void run() {
		podCommand pCommand = null;
		podResponse pResponse = null;
		byte[] respBytes = null;

		long eventtime;
		Intent downIntent = null;
		KeyEvent downEvent = null;
		Intent upIntent = null;
		KeyEvent upEvent = null;

		// serialWrite(new byte[] { (byte) 0xFF, 0x55, 0x02, 0x00, 0x00,
		// (byte) 0xFE });

		while (mPodRunning) {
			pCommand = readCommand();
			if (pCommand == null)
				continue;

			if (pCommand.mode == 0x03)
				mPodStatus = podStat.DISPLAYREMOTE;

			if (mAccessoryName != null
					&& (mPodStatus == podStat.SIMPLEREMOTE
							|| mPodStatus == podStat.DISPLAYREMOTE
							|| mPodStatus == podStat.ADVANCEDREMOTE || mPodStatus == podStat.ADVANCEDHACK)
					&& mHTTPsend) {
				mHTTPsend = false;
				mHttpThread.start();
			}

			if (mLaunchFirstTime
					&& (pCommand.mode == 02 || (pCommand.mode == 04 && !mAdvancedRemoteApp
							.equals(PACKAGENAME)))) {

				String launchApp = null;

				if (pCommand.mode == 02) {
					mBanner = getString(R.string.simple_remote);
					launchApp = mSimpleRemoteApp;
				}

				if (pCommand.mode == 04) {
					mPodStatus = podStat.ADVANCEDREMOTE;
					if (!mAdvancedRemoteApp.equals(PACKAGENAME))
						mPodStatus = podStat.ADVANCEDHACK;

					mBanner = getString(R.string.advanced_remote);
					launchApp = mAdvancedRemoteApp;
				}

				if (launchApp != null) {
					PackageManager pm = getPackageManager();
					Intent LaunchIntent = pm
							.getLaunchIntentForPackage(launchApp);

					startActivity(LaunchIntent);

					ResolveInfo info = pm.resolveActivity(LaunchIntent,
							PackageManager.MATCH_DEFAULT_ONLY);
					mSongTitle = (String) info.loadLabel(pm);
					mElapsedTime = 0;

					if (pCommand.mode == 04)
						mRetriever.changeApp(false, mSongTitle,
								getString(R.string.advanced_remote), 999);

					mAlbumArtUri = "android.resource://" + launchApp + "/"
							+ String.valueOf(info.getIconResource());
					setUpAsForeground(mBanner);
					localBroadcast(false);
				}

				mLaunchFirstTime = false;
			}

			if (pCommand.mode == 02) {

				mPodStatus = podStat.SIMPLEREMOTE;

				switch (pCommand.command) {

				case RemoteRelease:
					respBytes = new byte[] { (byte) 0x01, 0x00, 0x00 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case RemotePlayPause:

					eventtime = SystemClock.uptimeMillis();

					downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
					downEvent = new KeyEvent(eventtime, eventtime,
							KeyEvent.ACTION_DOWN,
							KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0);
					downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
					if (mSimpleRemoteApp != null)
						downIntent.setPackage(mSimpleRemoteApp);

					sendOrderedBroadcast(downIntent, null);

					break;

				case RemoteJustPlay:

					eventtime = SystemClock.uptimeMillis();

					downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
					downEvent = new KeyEvent(eventtime, eventtime,
							KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY,
							0);
					downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
					if (mSimpleRemoteApp != null)
						downIntent.setPackage(mSimpleRemoteApp);

					sendOrderedBroadcast(downIntent, null);

					break;

				case RemoteJustPause:

					eventtime = SystemClock.uptimeMillis();

					downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
					downEvent = new KeyEvent(eventtime, eventtime,
							KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE,
							0);
					downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
					if (mSimpleRemoteApp != null)
						downIntent.setPackage(mSimpleRemoteApp);

					sendOrderedBroadcast(downIntent, null);

					break;

				case RemoteSkipFwd:

					eventtime = SystemClock.uptimeMillis();

					if (downEvent != null) {
						if (downEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT
								|| downEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
							if ((eventtime - downEvent.getEventTime()) > 1000) {

								downIntent = new Intent(
										Intent.ACTION_MEDIA_BUTTON, null);
								downEvent = new KeyEvent(eventtime, eventtime,
										KeyEvent.ACTION_DOWN,
										KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 0);
								downIntent.putExtra(Intent.EXTRA_KEY_EVENT,
										downEvent);
								if (mSimpleRemoteApp != null)
									downIntent.setPackage(mSimpleRemoteApp);

								sendOrderedBroadcast(downIntent, null);

								upIntent = new Intent(
										Intent.ACTION_MEDIA_BUTTON, null);
								upEvent = new KeyEvent(eventtime, eventtime,
										KeyEvent.ACTION_UP,
										KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 0);
								upIntent.putExtra(Intent.EXTRA_KEY_EVENT,
										upEvent);
								if (mSimpleRemoteApp != null)
									upIntent.setPackage(mSimpleRemoteApp);

								sendOrderedBroadcast(upIntent, null);
							}

						}

					} else {

						downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON,
								null);
						downEvent = new KeyEvent(eventtime, eventtime,
								KeyEvent.ACTION_DOWN,
								KeyEvent.KEYCODE_MEDIA_NEXT, 0);
						downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
						if (mSimpleRemoteApp != null)
							downIntent.setPackage(mSimpleRemoteApp);
					}

					break;
				case RemoteSkipRwd:

					eventtime = SystemClock.uptimeMillis();

					if (downEvent != null) {
						if (downEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PREVIOUS
								|| downEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND) {
							if ((eventtime - downEvent.getEventTime()) > 1000) {

								downIntent = new Intent(
										Intent.ACTION_MEDIA_BUTTON, null);
								downEvent = new KeyEvent(eventtime, eventtime,
										KeyEvent.ACTION_DOWN,
										KeyEvent.KEYCODE_MEDIA_REWIND, 0);
								downIntent.putExtra(Intent.EXTRA_KEY_EVENT,
										downEvent);
								if (mSimpleRemoteApp != null)
									downIntent.setPackage(mSimpleRemoteApp);
								sendOrderedBroadcast(downIntent, null);

								upIntent = new Intent(
										Intent.ACTION_MEDIA_BUTTON, null);
								upEvent = new KeyEvent(eventtime, eventtime,
										KeyEvent.ACTION_UP,
										KeyEvent.KEYCODE_MEDIA_REWIND, 0);
								upIntent.putExtra(Intent.EXTRA_KEY_EVENT,
										upEvent);
								if (mSimpleRemoteApp != null)
									upIntent.setPackage(mSimpleRemoteApp);
								sendOrderedBroadcast(upIntent, null);
							}

						}

					} else {

						downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON,
								null);
						downEvent = new KeyEvent(eventtime, eventtime,
								KeyEvent.ACTION_DOWN,
								KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0);
						downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
						if (mSimpleRemoteApp != null)
							downIntent.setPackage(mSimpleRemoteApp);
					}

					break;
				case RemoteStop:
					eventtime = SystemClock.uptimeMillis();

					downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
					downEvent = new KeyEvent(eventtime, eventtime,
							KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP,
							0);
					downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
					if (mSimpleRemoteApp != null)
						downIntent.setPackage(mSimpleRemoteApp);
					sendOrderedBroadcast(downIntent, null);

					break;

				case RemoteButtonRel:

					eventtime = SystemClock.uptimeMillis();

					if (downIntent != null) {
						if (downEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT
								|| downEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PREVIOUS)
							sendOrderedBroadcast(downIntent, null);

						if (downEvent.getKeyCode() != KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
								&& downEvent.getKeyCode() != KeyEvent.KEYCODE_MEDIA_REWIND) {
							upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON,
									null);
							upEvent = new KeyEvent(downEvent.getDownTime(),
									eventtime, KeyEvent.ACTION_UP,
									downEvent.getKeyCode(), 0);
							upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
							if (mSimpleRemoteApp != null)
								upIntent.setPackage(mSimpleRemoteApp);
							sendOrderedBroadcast(upIntent, null);
						}

					}
					downIntent = null;
					downEvent = null;
					upIntent = null;
					upEvent = null;

					break;
				default:
					break;

				}

			} else {

				switch (pCommand.command) {

				case AppCmd:
					byte[] appNameBytes = new byte[pCommand.params.length - 3];

					System.arraycopy(pCommand.params, 2, appNameBytes, 0,
							appNameBytes.length);

					String appName = "";

					try {
						appName = new String(appNameBytes, "UTF8");
						Log.d("PodMode", "AppCmd " + appName);
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}

					break;

				case AppAck:
					break;

				case GetUpdateFlag:
					respBytes = new byte[] { 0x00, 0x0A, mUpdateFlag };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;
				case SetUpdateFlag:
					mUpdateFlag = pCommand.params[0];
					respBytes = new byte[] { 0x00, 0x01 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;
				case SwitchToMainPlaylist:
					if (mPodStatus == podStat.ADVANCEDHACK) {
						mNowPlaying = 0;
						mPrevPlaying = 0;
					} else
						mNowPlaying = 0;

					mRetriever.switchToMainPlaylist();
					respBytes = new byte[] { 0x00, 0x01 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;
				case SwitchToItem:

					int itemNo = 0;

					itemNo = pCommand.params[4] & 0xFF;
					itemNo += ((pCommand.params[3] & 0xFF) << 8);
					itemNo += ((pCommand.params[2] & 0xFF) << 16);
					itemNo += ((pCommand.params[1] & 0xFF) << 24);

					if ((mPodStatus == podStat.ADVANCEDHACK && mNotifyHack)) {
						mNotifyHack = false;
					} else {
						if (mRetriever.switchToItem((int) pCommand.params[0],
								itemNo)) {
							if (pCommand.params[0] == (byte) 0x05) {
								mNowPlaying = itemNo;
								tryToGetAudioFocus();
								playNextSong(null);
							}
						}
					}

					respBytes = new byte[] { 0x00, 0x01 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetCountForType:
					respBytes = new byte[] { 0x00, 0x19, 0x00, 0x00, 0x00, 0x00 };
					int num = mRetriever
							.getCountForType((int) pCommand.params[0]);

					respBytes[5] = (byte) (num & 0xFF);
					respBytes[4] = (byte) ((num >> 8) & 0xFF);
					respBytes[3] = (byte) ((num >> 16) & 0xFF);
					respBytes[2] = (byte) ((num >> 24) & 0xFF);

					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetItemNames:
					int startPos = 0;
					int count = 0;

					startPos = pCommand.params[4] & 0xFF;
					startPos += ((pCommand.params[3] & 0xFF) << 8);
					startPos += ((pCommand.params[2] & 0xFF) << 16);
					startPos += ((pCommand.params[1] & 0xFF) << 24);

					count = pCommand.params[8] & 0xFF;
					count += ((pCommand.params[7] & 0xFF) << 8);
					count += ((pCommand.params[6] & 0xFF) << 16);
					count += ((pCommand.params[5] & 0xFF) << 24);

					String[] itemNames = mRetriever.GetItemNames(
							(int) pCommand.params[0], startPos, count);

					if (itemNames != null) {
						for (int i = 0; i < itemNames.length; i++) {
							byte[] part1 = { (byte) 0x00, (byte) 0x1B,
									(byte) (startPos >>> 24),
									(byte) (startPos >>> 16),
									(byte) (startPos >>> 8), (byte) startPos };

							startPos++;

							respBytes = new String(new String(part1)
									+ itemNames[i] + '\0').getBytes();
							pResponse = new podResponse(pCommand, respBytes);
							serialWrite(pResponse.getBytes());
						}
					}
					break;

				case GetTimeStatus:
					respBytes = new byte[] { 0x00, 0x1D, 0x00, 0x00, 0x00,
							0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };

					if (mState != State.Preparing && mState != State.Retrieving) {

						int trackLen = 0;

						if (mPlayer != null)
							trackLen = mPlayer.getDuration();
						respBytes[2] = (byte) (trackLen >>> 24);
						respBytes[3] = (byte) (trackLen >>> 16);
						respBytes[4] = (byte) (trackLen >>> 8);
						respBytes[5] = (byte) trackLen;

						int elapsedTime = 0;
						if (mPlayer != null)
							elapsedTime = mPlayer.getCurrentPosition();

						respBytes[6] = (byte) (elapsedTime >>> 24);
						respBytes[7] = (byte) (elapsedTime >>> 16);
						respBytes[8] = (byte) (elapsedTime >>> 8);
						respBytes[9] = (byte) elapsedTime;

						switch (mState) {
						case Stopped:
							respBytes[10] = (byte) 0x00;
							break;
						case Playing:
							respBytes[10] = (byte) 0x01;
							break;
						case Paused:
							respBytes[10] = (byte) 0x02;
							break;
						case Preparing:
							respBytes[10] = (byte) 0x01;
							break;
						case Retrieving:
							respBytes[10] = (byte) 0x01;
							break;
						}
					}

					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetPlaylistPos:
					respBytes = new byte[] { 0x00, 0x1F, 0x00, 0x00, 0x00, 0x00 };

					respBytes[2] = (byte) ((mNowPlaying) >>> 24);
					respBytes[3] = (byte) ((mNowPlaying) >>> 16);
					respBytes[4] = (byte) ((mNowPlaying) >>> 8);
					respBytes[5] = (byte) mNowPlaying;

					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetSongTitle:
					byte[] part1 = new byte[] { 0x00, 0x21 };
					int index;
					index = pCommand.params[3] & 0xFF;
					index += ((pCommand.params[2] & 0xFF) << 8);
					index += ((pCommand.params[1] & 0xFF) << 16);
					index += ((pCommand.params[0] & 0xFF) << 24);

					if (index == -1)
						index = 0;

					respBytes = new String(new String(part1)
							+ mRetriever.getTrack(index).title + '\0')
							.getBytes();

					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetSongArtist:
					part1 = new byte[] { 0x00, 0x23 };
					index = pCommand.params[3] & 0xFF;
					index += ((pCommand.params[2] & 0xFF) << 8);
					index += ((pCommand.params[1] & 0xFF) << 16);
					index += ((pCommand.params[0] & 0xFF) << 24);

					if (index == -1)
						index = 0;

					respBytes = new String(new String(part1)
							+ mRetriever.getTrack(index).artist + '\0')
							.getBytes();

					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetSongAlbum:
					part1 = new byte[] { 0x00, 0x25 };
					index = pCommand.params[3] & 0xFF;
					index += ((pCommand.params[2] & 0xFF) << 8);
					index += ((pCommand.params[1] & 0xFF) << 16);
					index += ((pCommand.params[0] & 0xFF) << 24);

					if (index == -1)
						index = 0;

					respBytes = new String(new String(part1)
							+ mRetriever.getTrack(index).album + '\0')
							.getBytes();

					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case PollingMode:
					respBytes = new byte[] { 0x00, 0x01 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());

					mPollSpeed = (byte) pCommand.params[0];
					if (pCommand.params[0] == (byte) 0x01
							&& mUpdateFlag != (byte) 0x01) {
						mUpdateFlag = pCommand.params[0];

						if (mMediaChangeTimer == null)
							mMediaChangeTimer = new Timer();

						mMediaChangeTimer.scheduleAtFixedRate(mMediaChangeTask,
								0, 500);

					} else if (pCommand.params[0] == (byte) 0x00
							&& mUpdateFlag != (byte) 0x00) {

						mUpdateFlag = pCommand.params[0];
						if (mMediaChangeTimer != null)
							mMediaChangeTimer.cancel();
					}

					break;

				case ExecPlaylist:
					itemNo = pCommand.params[3] & 0xFF;
					itemNo += ((pCommand.params[2] & 0xFF) << 8);
					itemNo += ((pCommand.params[1] & 0xFF) << 16);
					itemNo += ((pCommand.params[0] & 0xFF) << 24);

					if (itemNo == -1)
						itemNo = 0;

					mRetriever.ExecPlaylist();

					if (mPodShuffleMode == modeStat.Songs)
						mRetriever.shuffleTracks();

					mNowPlaying = itemNo;
					tryToGetAudioFocus();
					playNextSong(null);

					respBytes = new byte[] { 0x00, 0x01 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case PlaybackControl:
					switch (pCommand.params[0]) {
					case 0x01:
						// processStopRequest();
						processTogglePlaybackRequest();
						break;
					case 0x02:
						processStopRequest();
						break;
					case 0x03:
						processPauseRequest();
						processSkipRequest();
						break;
					case 0x04:
						processPauseRequest();
						processSkipRwdRequest();
						break;
					case 0x05:
						// processSkipRequest();
						break;
					case 0x06:
						// processRewindRequest();
						break;
					case 0x07:
						// TODO Add Stop FF/RR function
						break;
					}

					respBytes = new byte[] { 0x00, 0x01 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetPlayListSongNum:
					respBytes = new byte[] { 0x00, 0x36, 0x00, 0x00, 0x00, 0x00 };
					num = mRetriever.getCount();

					respBytes[5] = (byte) (num & 0xFF);
					respBytes[4] = (byte) ((num >> 8) & 0xFF);
					respBytes[3] = (byte) ((num >> 16) & 0xFF);
					respBytes[2] = (byte) ((num >> 24) & 0xFF);

					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case JumpToSong:
					itemNo = pCommand.params[3] & 0xFF;
					itemNo += ((pCommand.params[2] & 0xFF) << 8);
					itemNo += ((pCommand.params[1] & 0xFF) << 16);
					itemNo += ((pCommand.params[0] & 0xFF) << 24);

					mNowPlaying = itemNo;
					tryToGetAudioFocus();
					playNextSong(null);

					respBytes = new byte[] { 0x00, 0x01 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case FrankPlaylist:
					respBytes = new byte[] { 0x00, 0x4F, (byte) 0xFF,
							(byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
					num = mRetriever.getPlaylistNum();

					if (num != -1) {
						respBytes[5] = (byte) (num & 0xFF);
						respBytes[4] = (byte) ((num >> 8) & 0xFF);
						respBytes[3] = (byte) ((num >> 16) & 0xFF);
						respBytes[2] = (byte) ((num >> 24) & 0xFF);
					}

					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case StartID:
					respBytes = new byte[] { 0x02 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetPodProtocols:
					// start
					respBytes = new byte[] { 0x02 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					if (pCommand.rawBytes[13] == (byte) 0x00
							&& pCommand.rawBytes[14] == (byte) 0x00
							&& pCommand.rawBytes[15] == (byte) 0x00
							&& pCommand.rawBytes[16] == (byte) 0x00) {
						// respBytes = new byte[] { 0x00 };
						// pResponse = new podResponse(pCommand, respBytes);
						// serialWrite(pResponse.getBytes());

					} else {
						respBytes = new byte[] { 0x14 };
						pResponse = new podResponse(pCommand, respBytes);
						serialWrite(pResponse.getBytes());
					}

					break;

				case DeviceAuthInfo:
					if (pCommand.length == 4) {
						respBytes = new byte[] { 0x16 };
						pResponse = new podResponse(pCommand, respBytes);
						serialWrite(pResponse.getBytes());

						respBytes = new byte[] { 0x17, 0x01 };
						pResponse = new podResponse(pCommand, respBytes);
						serialWrite(pResponse.getBytes());
					} else {

						if (pCommand.rawBytes[7] != pCommand.rawBytes[8]) {
							respBytes = new byte[] { 0x02 };
							pResponse = new podResponse(pCommand, respBytes);
							serialWrite(pResponse.getBytes());

						} else {
							respBytes = new byte[] { 0x16 };
							pResponse = new podResponse(pCommand, respBytes);
							serialWrite(pResponse.getBytes());

							respBytes = new byte[] { 0x17, 0x02 };
							pResponse = new podResponse(pCommand, respBytes);
							serialWrite(pResponse.getBytes());
						}
					}

					break;

				case DeviceAuthSig:
					respBytes = new byte[] { 0x19 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetPodOptions:
					// start
					if (pCommand.rawBytes[5] == 0x00)
						respBytes = new byte[] { 0x4C };
					else
						respBytes = new byte[] { (byte) 0x02, 0x04 };

					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetPodOption:
					// start
					respBytes = new byte[] { 0x25 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case SetIdTokens:
					respBytes = new byte[] { 0x3A };
					pResponse = new podResponse(pCommand, respBytes);
					if (mAccessoryName == null) {
						mAccessoryName = pResponse.accessoryName;
						mAccessoryMnf = pResponse.accessoryMnf;
						mAccessoryModel = pResponse.accessoryModel;
						mHTTPsend = true;
					}
					serialWrite(pResponse.getBytes());
					break;

				case EndID:
					respBytes = new byte[] { 0x3C };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());

					respBytes = new byte[] { 0x14 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetProtoVersion:
					respBytes = new byte[] { 0x0F };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case DeviceDetails:
					respBytes = new byte[] { 0x02 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case DevName:
					respBytes = new byte[] { 0x00, 0x15, 0x50, 0x6F, 0x64,
							0x4D, 0x6F, 0x64, 0x65, 0x00 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case DevTypeSize:
					pResponse = new podResponse(pCommand, DEVTYPESIZE);
					serialWrite(pResponse.getBytes());
					break;

				case StateInfo:
					respBytes = new byte[] { 0x0D };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case RemoteNotify:
					respBytes = new byte[] { 0x02 };
					pResponse = new podResponse(pCommand, respBytes);

					serialWrite(pResponse.getBytes());
					break;

				case SwitchRemote:
					mPodStatus = podStat.SIMPLEREMOTE;
					break;

				case ReqAdvRemote:
					respBytes = new byte[] { 0x04, 0x00 };

					if (mPodStatus == podStat.ADVANCEDREMOTE
							|| mPodStatus == podStat.ADVANCEDHACK)
						respBytes[1] = 0x04;

					pResponse = new podResponse(pCommand, respBytes);

					serialWrite(pResponse.getBytes());
					break;

				case StartAdvRemote:
					mPodStatus = podStat.ADVANCEDREMOTE;
					if (!mAdvancedRemoteApp.equals(PACKAGENAME))
						mPodStatus = podStat.ADVANCEDHACK;

					respBytes = new byte[] { 0x2 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case EndAdvRemote:
					mPodStatus = podStat.WAITING;
					respBytes = new byte[] { 0x2 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetSoftVersion:
					respBytes = new byte[] { 0x0A };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetSerialNum:
					respBytes = new byte[] { 0x0C };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case DevModel:
					respBytes = new byte[] { 0x0E };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case SwitchAdvanced:
					mPodStatus = podStat.ADVANCEDREMOTE;
					if (!mAdvancedRemoteApp.equals(PACKAGENAME))
						mPodStatus = podStat.ADVANCEDHACK;
					break;

				case SetRepeatMode:
					if (pCommand.params[0] == (byte) 0x00)
						mPodRepeatMode = modeStat.Off;
					else if (pCommand.params[0] == (byte) 0x01)
						mPodRepeatMode = modeStat.Songs;
					else
						mPodRepeatMode = modeStat.Albums;
					respBytes = new byte[] { 0x00, 0x01 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetRepeatMode:
					respBytes = new byte[] { 0x00, 0x30, 0x00 };
					if (mPodRepeatMode == modeStat.Songs)
						respBytes[2] = 0x01;
					if (mPodRepeatMode == modeStat.Albums)
						respBytes[2] = 0x02;
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case SetShuffleMode:
					if (pCommand.params[0] == (byte) 0x00)
						mPodShuffleMode = modeStat.Off;
					else if (pCommand.params[0] == (byte) 0x01)
						mPodShuffleMode = modeStat.Songs;
					else
						mPodShuffleMode = modeStat.Albums;

					respBytes = new byte[] { 0x00, 0x01 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetShuffleMode:
					respBytes = new byte[] { 0x00, 0x2D, 0x00 };
					if (mPodShuffleMode == modeStat.Songs)
						respBytes[2] = 0x01;
					if (mPodShuffleMode == modeStat.Albums)
						respBytes[2] = 0x02;
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				case GetScreenSize:
					respBytes = new byte[] { 0x00, 0x34 };
					pResponse = new podResponse(pCommand, respBytes);
					serialWrite(pResponse.getBytes());
					break;

				default:
					break;
				}

			}
		}
	}

	private podCommand readCommand() {
		byte[] rbuf = new byte[4096];
		int len = 0;
		boolean notEnoughData = false;

		while (mPodRunning) {

			if (Thread.interrupted())
				return null;

			if (mReadLen < 6 || notEnoughData) {

				try {
					len = mSerialRead(rbuf);
				} catch (NullPointerException e) {
					Log.d("PodMode", "Read failed - Null pointer");
					mPodRunning = false;
				}

				if (len > 0) {
					System.arraycopy(rbuf, 0, mReadBuffer, mReadLen, len);
					mReadLen += len;
					notEnoughData = false;
				} else if (len < 0) {
					Log.d("PodMode read error : ", String.valueOf(len));

					if (mDeviceType == deviceType.FT232PL2303) {
						mSerialHost = new FTDriver(
								(UsbManager) getSystemService(Context.USB_SERVICE));
						mSerialHost.begin(mSerialBaudRate);
					}
				}

			} else {

				if (mReadBuffer[0] == (byte) 0xFF
						&& mReadBuffer[1] == (byte) 0x55
						&& mReadBuffer[2] == (byte) 0xF7) {
					System.arraycopy(mReadBuffer, 3, mReadBuffer, 0,
							mReadLen - 3);
					mReadLen -= 3;
				}

				if (mReadBuffer[0] == (byte) 0xFF
						&& mReadBuffer[1] == (byte) 0x55) {

					int readBufLen = (int) (mReadBuffer[2] & 0xFF);
					int cmdLen = readBufLen + 4;

					if (mReadLen >= cmdLen) {

						podCommand cmd = new podCommand(mReadBuffer, cmdLen);

						if (mReadLen == cmdLen)
							mReadLen = 0;
						else {
							System.arraycopy(mReadBuffer, cmdLen, mReadBuffer,
									0, mReadLen - cmdLen);
							mReadLen -= cmdLen;
						}

						return cmd;

					} else
						notEnoughData = true;

				} else {

					if (mReadLen == 1) {
						if (mReadBuffer[0] != (byte) 0xFF) {
							mReadLen = 0;
						}
					} else {
						System.arraycopy(mReadBuffer, 1, mReadBuffer, 0,
								mReadLen - 1);
						mReadLen -= 1;
					}
				}

			}
		}
		return null;
	}

	void processTogglePlaybackRequest() {
		if (mPodStatus == podStat.ADVANCEDHACK) {
			broadcastMediaButtons(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
					mAdvancedRemoteApp);
			return;
		}

		if (mState == State.Paused || mState == State.Stopped) {
			processPlayRequest();
		} else {
			processPauseRequest();
		}
	}

	void processPlayRequest() {
		if (mState == State.Retrieving) {
			// If we are still retrieving media, just set the flag to start
			// playing when we're
			// ready
			mWhatToPlayAfterRetrieve = null; // play a random song
			mStartPlayingAfterRetrieve = true;
			return;
		}

		tryToGetAudioFocus();

		// actually play the song

		if (mState == State.Stopped) {
			// If we're stopped, just go ahead to the next song and start
			// playing
			playNextSong(null);
		} else if (mState == State.Paused) {
			// If we're paused, just continue playback and restore the
			// 'foreground service' state.
			mState = State.Playing;
			setUpAsForeground(mSongTitle + getString(R.string.notify_playing));
			configAndStartMediaPlayer();
		}

		// Tell any remote controls that our playback state is 'playing'.

	}

	void processPauseRequest() {
		if (mPodStatus == podStat.ADVANCEDHACK) {
			broadcastMediaButtons(KeyEvent.KEYCODE_MEDIA_PAUSE,
					mAdvancedRemoteApp);
			return;
		}

		if (mState == State.Retrieving) {
			// If we are still retrieving media, clear the flag that indicates
			// we should start
			// playing when we're ready
			mStartPlayingAfterRetrieve = false;
			return;
		}

		if (mState == State.Playing) {
			// Pause media player and cancel the 'foreground service' state.
			mState = State.Paused;
			mPlayer.pause();
			relaxResources(false); // while paused, we always retain the
									// MediaPlayer
			// do not give up audio focus
		}

	}

	void processRewindRequest() {
		if (mPodStatus == podStat.ADVANCEDHACK) {
			broadcastMediaButtons(KeyEvent.KEYCODE_MEDIA_REWIND,
					mAdvancedRemoteApp);
			return;
		}

		if (mState == State.Playing || mState == State.Paused)
			mPlayer.seekTo(0);
	}

	void processSkipRequest() {
		if (mPodStatus == podStat.ADVANCEDHACK) {
			mNowPlaying++;
			broadcastMediaButtons(KeyEvent.KEYCODE_MEDIA_NEXT,
					mAdvancedRemoteApp);
			return;
		}

		if (mState == State.Playing || mState == State.Paused) {
			mNowPlaying++;
			tryToGetAudioFocus();
			playNextSong(null);
		}
	}

	void processSkipRwdRequest() {
		if (mPodStatus == podStat.ADVANCEDHACK) {
			mNowPlaying--;
			broadcastMediaButtons(KeyEvent.KEYCODE_MEDIA_PREVIOUS,
					mAdvancedRemoteApp);
			return;
		}

		if (mState == State.Playing || mState == State.Paused) {
			mNowPlaying--;
			if (mNowPlaying < 0)
				mNowPlaying = -1;
			tryToGetAudioFocus();
			playNextSong(null);
		}
	}

	void processStopRequest() {
		if (mPodStatus == podStat.ADVANCEDHACK) {
			broadcastMediaButtons(KeyEvent.KEYCODE_MEDIA_STOP,
					mAdvancedRemoteApp);
			return;
		}

		processStopRequest(false);
	}

	void processStopRequest(boolean force) {
		if (mState == State.Playing || mState == State.Paused || force) {
			mState = State.Stopped;

			// let go of all resources...
			relaxResources(true);
			giveUpAudioFocus();

		}
	}

	/**
	 * Makes sure the media player exists and has been reset. This will create
	 * the media player if needed, or reset the existing media player if one
	 * already exists.
	 */
	void createMediaPlayerIfNeeded() {
		if (mPlayer == null) {
			mPlayer = new MediaPlayer();

			// Make sure the media player will acquire a wake-lock while
			// playing. If we don't do
			// that, the CPU might go to sleep while the song is playing,
			// causing playback to stop.
			//
			// Remember that to use this, we have to declare the
			// android.permission.WAKE_LOCK
			// permission in AndroidManifest.xml.
			mPlayer.setWakeMode(getApplicationContext(),
					PowerManager.PARTIAL_WAKE_LOCK);

			// we want the media player to notify us when it's ready preparing,
			// and when it's done
			// playing:
			mPlayer.setOnPreparedListener(this);
			mPlayer.setOnCompletionListener(this);
			mPlayer.setOnErrorListener(this);
		} else
			mPlayer.reset();
	}

	/**
	 * Releases resources used by the service for playback. This includes the
	 * "foreground service" status and notification, the wake locks and possibly
	 * the MediaPlayer.
	 * 
	 * @param releaseMediaPlayer
	 *            Indicates whether the Media Player should also be released or
	 *            not
	 */
	void relaxResources(boolean releaseMediaPlayer) {
		// stop being a foreground service
		stopForeground(true);

		// stop and release the Media Player, if it's available
		if (releaseMediaPlayer && mPlayer != null) {
			mPlayer.reset();
			mPlayer.release();
			mPlayer = null;
		}

		// we can also release the Wifi lock, if we're holding it
		if (mWifiLock.isHeld())
			mWifiLock.release();
	}

	void giveUpAudioFocus() {
		if (mAudioFocus == AudioFocus.Focused && mAudioFocusHelper != null
				&& mAudioFocusHelper.abandonFocus())
			mAudioFocus = AudioFocus.NoFocusNoDuck;
	}

	/**
	 * Reconfigures MediaPlayer according to audio focus settings and
	 * starts/restarts it. This method starts/restarts the MediaPlayer
	 * respecting the current audio focus state. So if we have focus, it will
	 * play normally; if we don't have focus, it will either leave the
	 * MediaPlayer paused or set it to a low volume, depending on what is
	 * allowed by the current focus settings. This method assumes mPlayer !=
	 * null, so if you are calling it, you have to do so from a context where
	 * you are sure this is the case.
	 */
	void configAndStartMediaPlayer() {
		if (mAudioFocus == AudioFocus.NoFocusNoDuck) {
			// If we don't have audio focus and can't duck, we have to pause,
			// even if mState
			// is State.Playing. But we stay in the Playing state so that we
			// know we have to resume
			// playback once we get the focus back.
			if (mPlayer.isPlaying())
				mPlayer.pause();
			return;
		} else if (mAudioFocus == AudioFocus.NoFocusCanDuck)
			mPlayer.setVolume(DUCK_VOLUME, DUCK_VOLUME); // we'll be relatively
															// quiet
		else
			mPlayer.setVolume(1.0f, 1.0f); // we can be loud

		if (!mPlayer.isPlaying())
			mPlayer.start();
	}

	void processAddRequest(Intent intent) {
		// user wants to play a song directly by URL or path. The URL or path
		// comes in the "data"
		// part of the Intent. This Intent is sent by {@link MainActivity} after
		// the user
		// specifies the URL/path via an alert box.
		if (mState == State.Retrieving) {
			// we'll play the requested URL right after we finish retrieving
			mWhatToPlayAfterRetrieve = intent.getData();
			mStartPlayingAfterRetrieve = true;
		} else if (mState == State.Playing || mState == State.Paused
				|| mState == State.Stopped) {
			Log.i(TAG, "Playing from URL/path: " + intent.getData().toString());
			tryToGetAudioFocus();
			playNextSong(intent.getData().toString());
		}
	}

	void tryToGetAudioFocus() {
		if (mPodStatus == podStat.ADVANCEDHACK)
			return;

		if (mAudioFocus != AudioFocus.Focused && mAudioFocusHelper != null
				&& mAudioFocusHelper.requestFocus())
			mAudioFocus = AudioFocus.Focused;
	}

	/**
	 * Starts playing the next song. If manualUrl is null, the next song will be
	 * randomly selected from our Media Retriever (that is, it will be a random
	 * song in the user's device). If manualUrl is non-null, then it specifies
	 * the URL or path to the song that will be played next.
	 */
	void playNextSong(String manualUrl) {
		if (mPodStatus == podStat.ADVANCEDHACK) {

			if ((mNowPlaying == 2 && mPrevPlaying == 1)
					|| (mNowPlaying == 0 && mPrevPlaying == 2)
					|| (mNowPlaying == 1 && mPrevPlaying == 0)) {
				broadcastMediaButtons(KeyEvent.KEYCODE_MEDIA_NEXT,
						mAdvancedRemoteApp);
			}
			if ((mNowPlaying == 1 && mPrevPlaying == 2)
					|| (mNowPlaying == 0 && mPrevPlaying == 1)
					|| (mNowPlaying == 2 && mPrevPlaying == 0)) {
				broadcastMediaButtons(KeyEvent.KEYCODE_MEDIA_PREVIOUS,
						mAdvancedRemoteApp);
			}
			if (mNowPlaying == mPrevPlaying && mPrevPlaying == 0) {
				broadcastMediaButtons(KeyEvent.KEYCODE_MEDIA_PLAY,
						mAdvancedRemoteApp);
			}

			mPrevPlaying = mNowPlaying;
			return;
		}

		mState = State.Stopped;
		relaxResources(false); // release everything except MediaPlayer

		MusicRetriever.Track playingItem = null;
		if (manualUrl != null) {
			// set the source of the media player to a manual URL or path
			createMediaPlayerIfNeeded();
			mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			try {
				mPlayer.setDataSource(manualUrl);
			} catch (Exception e) {

			}
			mIsStreaming = manualUrl.startsWith("http:")
					|| manualUrl.startsWith("https:");

			playingItem = new Track(0, null, manualUrl, null, null, 0);
		} else {
			mIsStreaming = false; // playing a locally available song

			boolean IoErrorFlag = true;
			while (IoErrorFlag) {
				if (mNowPlaying >= 0 && mNowPlaying < mRetriever.getCount()) {
					playingItem = mRetriever.getTrack(mNowPlaying);
					if (playingItem == null) {
						mNowPlaying++;
					}
				} else {
					mRetriever.switchToMainPlaylist();
					mNowPlaying = 0;
					return;
				}

				// set the source of the media player to a content URI

				createMediaPlayerIfNeeded();
				mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				try {

					mPlayer.setDataSource(getApplicationContext(),
							playingItem.getURI());

					IoErrorFlag = false;

				} catch (IllegalStateException e) {
					mPlayer.reset();
				} catch (IOException f) {
					mNowPlaying++;
				}
			}
		}

		mSongTitle = playingItem.getTitle();
		mAlbumArtUri = playingItem.getAlbumArtUri();
		mElapsedTime = 0;

		mState = State.Preparing;
		setUpAsForeground(mSongTitle + getString(R.string.notify_loading));

		// Use the media button APIs (if available) to register ourselves
		// for media button
		// events

		// starts preparing the media player in the background. When it's
		// done, it will call
		// our OnPreparedListener (that is, the onPrepared() method on this
		// class, since we set
		// the listener to 'this').
		//
		// Until the media player is prepared, we *cannot* call start() on
		// it!

		int ioError = 0;
		while (ioError < 5) {
			try {
				mPlayer.prepareAsync();
				ioError = 5;
			} catch (NullPointerException e) {
				createMediaPlayerIfNeeded();
				mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				try {
					mPlayer.setDataSource(getApplicationContext(),
							playingItem.getURI());
				} catch (IllegalStateException e1) {
					mPlayer.reset();
					ioError++;
				} catch (IOException f) {
					f.printStackTrace();
					ioError = 5;
				}

			} catch (IllegalStateException e1) {
				mPlayer.reset();
				ioError++;
			}
		}

		mBanner = getString(R.string.now_playing);

		localBroadcast(false);

		// If we are streaming from the internet, we want to hold a Wifi
		// lock, which prevents
		// the Wifi radio from going to sleep while the song is playing. If,
		// on the other hand,
		// we are *not* streaming, we want to release the lock if we were
		// holding it before.
		if (mIsStreaming)
			mWifiLock.acquire();
		else if (mWifiLock.isHeld())
			mWifiLock.release();

	}

	/** Called when media player is done playing current song. */
	public void onCompletion(MediaPlayer player) {
		// The media player finished playing the current song, so we go ahead
		// and start the next.
		mChangedCounter = 1;
		processSkipRequest();
	}

	/** Called when media player is done preparing. */
	public void onPrepared(MediaPlayer player) {
		// The media player is done preparing. That means we can start playing!
		mState = State.Playing;
		updateNotification(mSongTitle + getString(R.string.notify_playing));
		configAndStartMediaPlayer();
	}

	/** Updates the notification. */
	void updateNotification(String text) {
		PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
				0, new Intent(getApplicationContext(), MainActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT);

		mNotification = new Notification.Builder(getApplicationContext())
				.setContentTitle("PodMode")
				.setContentText(text)
				.setContentIntent(pi)
				.setSmallIcon(R.drawable.ic_stat_podmode)
				.setLargeIcon(
						BitmapFactory.decodeResource(getResources(),
								R.drawable.ic_launcher)).setOngoing(true)
				.getNotification();

		mNotificationManager.notify(NOTIFICATION_ID, mNotification);
	}

	/**
	 * Configures service as a foreground service. A foreground service is a
	 * service that's doing something the user is actively aware of (such as
	 * playing music), and must appear to the user as a notification. That's why
	 * we create the notification here.
	 */
	void setUpAsForeground(String text) {
		PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
				0, new Intent(getApplicationContext(), MainActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT);

		mNotification = new Notification.Builder(getApplicationContext())
				.setContentTitle("PodMode")
				.setContentText(text)
				.setContentIntent(pi)
				.setSmallIcon(R.drawable.ic_stat_podmode)
				.setContentIntent(pi)
				.setLargeIcon(
						BitmapFactory.decodeResource(getResources(),
								R.drawable.ic_launcher)).setOngoing(true)
				.getNotification();

		startForeground(NOTIFICATION_ID, mNotification);
	}

	/**
	 * Called when there's an error playing media. When this happens, the media
	 * player goes to the Error state. We warn the user about the error and
	 * reset the media player.
	 */
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Toast.makeText(getApplicationContext(),
				"Media player error! Resetting.", Toast.LENGTH_SHORT).show();
		Log.e(TAG,
				"Error: what=" + String.valueOf(what) + ", extra="
						+ String.valueOf(extra));

		mState = State.Stopped;
		relaxResources(true);
		giveUpAudioFocus();
		return true; // true indicates we handled the error
	}

	public void onGainedAudioFocus() {
		// Toast.makeText(getApplicationContext(), "gained audio focus.",
		// Toast.LENGTH_SHORT).show();
		mAudioFocus = AudioFocus.Focused;

		// restart media player with new focus settings
		if (mState == State.Playing)
			configAndStartMediaPlayer();
	}

	public void onLostAudioFocus(boolean canDuck) {
		// Toast.makeText(getApplicationContext(),
		// "lost audio focus." + (canDuck ? "can duck" : "no duck"),
		// Toast.LENGTH_SHORT).show();
		mAudioFocus = canDuck ? AudioFocus.NoFocusCanDuck
				: AudioFocus.NoFocusNoDuck;

		// start/restart/pause media player with new focus settings
		if (mPlayer != null && mPlayer.isPlaying())
			configAndStartMediaPlayer();
	}

	private void localBroadcast(boolean launchMainActivity) {
		Intent localIntent = new Intent(MainActivity.BANNER);
		localIntent.putExtra("banner", mBanner);
		if (!mSongTitle.equals("")) {
			localIntent.putExtra("songname", mSongTitle);
			localIntent.putExtra("albumarturi", mAlbumArtUri);
		}

		if (launchMainActivity) {
			Intent launchIntent = new Intent(getBaseContext(),
					MainActivity.class);
			launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

			getApplication().startActivity(launchIntent);
		}

		LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
	}

	@Override
	public void onDestroy() {
		// Service is being killed, so make sure we release our resources
		mState = State.Stopped;
		mPodRunning = false;

		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		if ((mPodStatus == podStat.SIMPLEREMOTE || mPodStatus == podStat.ADVANCEDHACK)
				&& am.isMusicActive()) {
			broadcastMediaButtons(KeyEvent.KEYCODE_MEDIA_STOP, null);
		}

		// Stop the timer task
		if (mMediaChangeTimer != null)
			mMediaChangeTimer.cancel();

		// Stop the main thread
		if (mMainThread != null) {
			Thread moribund = mMainThread;
			mMainThread = null;
			moribund.interrupt();
		}

		// mProvider.dropRemoteControls(true);

		relaxResources(true);
		giveUpAudioFocus();

		if (mSerialHost != null)
			mSerialHost.end();

		if (mSerialDevice != null)
			mSerialDevice.DestroyAccessory(true);

		if (mBTDevice != null)
			mBTDevice.stop();

		if (podWakeLock != null)
			podWakeLock.release();

		unregisterReceiver(mUsbReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(
				mNotifyReceiver);

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);

		mRetriever.saveState(prefs, mNowPlaying);
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

}
