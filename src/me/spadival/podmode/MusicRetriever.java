package me.spadival.podmode;

/*   
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Retrieves and organizes media to play. Before being used, you must call
 * {@link #prepare()}, which will retrieve all of the music on the user's device
 * (by performing a query on a content resolver). After that, it's ready to
 * retrieve a random song, with its title and URI, upon request.
 */

public class MusicRetriever {
	final String TAG = "PodMode";

	ContentResolver mContentResolver;
	Context mContext;

	// the Tracks (songs) we have queried
	private List<Track> mNowPlaying = new ArrayList<Track>();

	private Track mDefaultTrack;

	private Random mRandom = new Random();

	private Cursor activeCursor = null;

	private String activePlaylist = "*";
	private long activePlaylistId = 0;
	private int activePlaylistNum = 0;

	private String activeGenre = "*";
	private long activeGenreId = 0;
	private String activeArtist = "*";
	private long activeArtistId = 0;
	private String activeAlbum = "*";
	private long activeAlbumId = 0;
	private String activeTrack = "*";
	private long activeTrackId = 0;
	private int categoryCount = 0;

	private boolean mPlayInApp = true;

	enum Category {
		All, Playlist, Artist, Album, Genre, Track
	};

	private Category activeCategory = null;

	public static class Track {
		long id;
		String artist;
		String title;
		String album;
		String albumArtUri;
		long duration;

		public Track(long id, String artist, String title, String album,
				String albumArtUri, long duration) {
			this.id = id;
			this.artist = artist;
			this.title = title;
			this.album = album;
			this.albumArtUri = albumArtUri;
			this.duration = duration;
		}

		public long getId() {
			return id;
		}

		public String getArtist() {
			return artist;
		}

		public String getTitle() {
			return title;
		}

		public String getAlbum() {
			return album;
		}

		public String getAlbumArtUri() {
			return albumArtUri;
		}

		public long getDuration() {
			return duration;
		}

		public Uri getURI() {
			// return ContentUris
			// .withAppendedId(
			// android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
			// id);

			return ContentUris.withAppendedId(
					MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

		}
	}

	public MusicRetriever(ContentResolver cr, Context context,
			boolean playInApp, SharedPreferences prefs) {
		mContentResolver = cr;
		mContext = context;
		mPlayInApp = playInApp;

		activePlaylist = prefs.getString("activePlaylist", "*");
		activePlaylistId = prefs.getLong("activePlaylistId", 0);
		activePlaylistNum = prefs.getInt("activePlaylistNum", 0);
		activeGenre = prefs.getString("activeGenre", "*");
		activeGenreId = prefs.getLong("activeGenreId", 0);
		activeArtist = prefs.getString("activeArtist", "*");
		activeArtistId = prefs.getLong("activeArtistId", 0);
		activeAlbum = prefs.getString("activeAlbum", "*");
		activeAlbumId = prefs.getLong("activeAlbumId", 0);
		activeTrack = prefs.getString("activeTrack", "*");
		activeTrackId = prefs.getLong("activeTrackId", 0);
	}

	public void saveState(SharedPreferences prefs, int nowPlaying) {

		SharedPreferences.Editor editor = prefs.edit();

		editor.putString("activePlaylist", activePlaylist);
		editor.putLong("activePlaylistId", activePlaylistId);
		editor.putInt("activePlaylistNum", activePlaylistNum);
		editor.putString("activeGenre", activeGenre);
		editor.putLong("activeGenreId", activeGenreId);
		editor.putString("activeArtist", activeArtist);
		editor.putLong("activeArtistId", activeArtistId);
		editor.putString("activeAlbum", activeAlbum);
		editor.putLong("activeAlbumId", activeAlbumId);
		editor.putString("activeTrack", activeTrack);
		editor.putLong("activeTrackId", activeTrackId);
		editor.putInt("nowplaying", nowPlaying);

		editor.commit();

	}

	public void changeApp(boolean playInApp, String appName, String text,
			long duration) {
		mPlayInApp = playInApp;

		if (!mPlayInApp) {
			setupDummyCursor();
			mDefaultTrack = new Track(0, appName, text, "PodMode", null,
					duration);
		}

		loadTracks();
	}

	public ContentResolver getContentResolver() {
		return mContentResolver;
	}

	public void prepare() {
		setupTrackCursor();
		if (activeCursor != null)
			categoryCount = activeCursor.getCount();
		loadTracks();
	}

	/** Returns a random Track. If there are no Tracks available, returns null. */
	public Track getRandomTrack() {
		if (mNowPlaying.size() <= 0)
			return null;
		return mNowPlaying.get(mRandom.nextInt(mNowPlaying.size()));
	}

	/**
	 * Returns the Track specified by TrackNum. If there are no Tracks
	 * available, returns null.
	 */

	public Track getTrack(int TrackNum) {
		if (mNowPlaying.size() <= 0 || TrackNum >= mNowPlaying.size()
				|| TrackNum < 0)
			return null;

		return mNowPlaying.get(TrackNum);
	}

	public int getCount() {

		return mNowPlaying.size();
	}

	public int getPlaylistNum() {

		if(activePlaylist.equals("*"))
			return -1;
		else
			return (int) activePlaylistNum;
	}

	public void shuffleTracks() {
		if (mNowPlaying.size() > 1)
			Collections.shuffle(mNowPlaying);
	}

	// ResetDBSelection - 0x16
	public void switchToMainPlaylist() {

		activeCategory = Category.All;
		activePlaylist = "*";
		activePlaylistId = 0;
		activeGenre = "*";
		activeGenreId = 0;
		activeArtist = "*";
		activeArtistId = 0;
		activeAlbum = "*";
		activeAlbumId = 0;
		activeTrack = "*";
		activeTrackId = 0;
		categoryCount = 0;

		setupTrackCursor();
		if (activeCursor != null)
			categoryCount = activeCursor.getCount();
	}

	public void closeActiveCursor() {
		if (activeCursor != null)
			if (!activeCursor.isClosed())
				activeCursor.close();
		categoryCount = 0;
	}

	// ReturnNumberCategorizedDBRecords - 0x18
	public int getCountForType(int type) {
		categoryCount = 0;

		switch (type) {
		case 1:
			setupPlaylistCursor();
			activeCategory = Category.Playlist;
			break;
		case 2:
			setupArtistCursor();
			activeCategory = Category.Artist;
			break;
		case 3:
			setupAlbumCursor();
			activeCategory = Category.Album;
			break;
		case 4:
			setupGenreCursor();
			categoryCount = 1;
			activeCategory = Category.Genre;
			break;
		case 5:
			setupTrackCursor();
			activeCategory = Category.Track;
			break;
		}

		if (activeCursor != null) {
			categoryCount = activeCursor.getCount();

			// activeCursor.moveToFirst();

			// for (int i = 0; i < categoryCount; i++) {
			// activeCursor.moveToNext();
			// }
		}

		return categoryCount;
	}

	// RetrieveCategorizedDatabaseRecords - 0x1A
	public String[] GetItemNames(int type, int start, int count) {

		int lastRec = 0;
		if (count == -1)
			lastRec = categoryCount - 1;
		else
			lastRec = start + count - 1;

		Category cat = Category.values()[type];

		// debugtest
		if (cat != activeCategory) {
			getCountForType(type);
		}

		if (categoryCount == 0 || categoryCount < lastRec + 1) {
			return null;
		}

		String[] dbRecs = new String[lastRec - start + 1];

		if (mPlayInApp) {
			activeCursor.moveToPosition(start);

			for (int idx = 0; idx <= (lastRec - start); idx++) {
				dbRecs[idx] = activeCursor.getString(0);
				activeCursor.moveToNext();
			}
		} else
			dbRecs[0] = "Advanced Remote";

		return dbRecs;

	}

	// SelectDBRecord - 0x17
	public boolean switchToItem(int type, int index) {

		getCountForType(type);

		if (categoryCount == 0)
			return false;
		if (index > categoryCount)
			return false;

		switch (type) {
		case 1:
			if (index == -1) {
				activePlaylist = "*";
				activePlaylistId = 0;
				activeGenre = "*";
				activeGenreId = 0;
				activeArtist = "*";
				activeArtistId = 0;
				activeAlbum = "*";
				activeAlbumId = 0;
				activeTrack = "*";
				activeTrackId = 0;
				categoryCount = 0;
				activeCategory = null;
			} else {
				activeCursor.moveToPosition(index);
				activePlaylist = activeCursor.getString(0);
				activePlaylistId = activeCursor.getLong(1);
				activePlaylistNum = index;
			}
			break;
		case 2:
			if (index == -1) {
				activeArtist = "*";
				activeArtistId = 0;
				activeAlbum = "*";
				activeAlbumId = 0;
				activeTrack = "*";
				activeTrackId = 0;
				categoryCount = 0;
				activeCategory = null;

			} else {
				activeCursor.moveToPosition(index);
				activeArtist = activeCursor.getString(0);
				activeArtistId = activeCursor.getLong(1);
			}
			break;
		case 3:
			if (index == -1) {
				activeAlbum = "*";
				activeAlbumId = 0;
				activeTrack = "*";
				activeTrackId = 0;
				categoryCount = 0;
				activeCategory = null;
			} else {
				activeCursor.moveToPosition(index);
				activeAlbum = activeCursor.getString(0);
				activeAlbumId = activeCursor.getLong(1);
			}

			break;
		case 4:
			if (index == -1) {
				activePlaylist = "*";
				activePlaylistId = 0;
				activeGenre = "*";
				activeGenreId = 0;
				activeArtist = "*";
				activeArtistId = 0;
				activeAlbum = "*";
				activeAlbumId = 0;
				activeTrack = "*";
				activeTrackId = 0;
				categoryCount = 0;
				activeCategory = null;

			} else {
				activeCursor.moveToPosition(index);
				activeGenre = activeCursor.getString(0);
				activeGenreId = activeCursor.getLong(1);
			}

			break;
		case 5:
			if (index == -1)
				return false;

			// activeCursor.moveToPosition(index);
			// activeTrack = activeCursor.getString(0);
			// activeTrackId = activeCursor.getLong(1);

			break;
		}

		getCountForType(type);

		if (type == 5)
			loadTracks();

		return true;
	}

	// PlayCurrentSelection - 0x28
	public void ExecPlaylist() {
		loadTracks();
	}

	private void loadTracks() {

		mNowPlaying.clear();

		if (mPlayInApp) {

			if (activeCategory != Category.All
					&& activeCategory != Category.Track)
				setupTrackCursor();

			if (activeCursor != null) {
				categoryCount = activeCursor.getCount();

				activeCursor.moveToFirst();

				for (int i = 0; i < categoryCount; i++) {

					long id;
					if (activeCategory == Category.Playlist)
						id = activeCursor
								.getLong(activeCursor
										.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID));
					else if (activeCategory == Category.Genre)
						id = activeCursor
								.getLong(activeCursor
										.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID));

					else

						id = activeCursor
								.getLong(activeCursor
										.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));

					String artist = activeCursor
							.getString(activeCursor
									.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
					String album = activeCursor
							.getString(activeCursor
									.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
					String title = activeCursor
							.getString(activeCursor
									.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
					long albumId = activeCursor
							.getLong(activeCursor
									.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));

					int duration = activeCursor
							.getInt(activeCursor
									.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));

					Uri sArtworkUri = Uri
							.parse("content://media/external/audio/albumart");
					String albumArtUri = ContentUris.withAppendedId(
							sArtworkUri, albumId).toString();

					mNowPlaying.add(new Track(id, artist, title, album,
							albumArtUri, duration));
					activeCursor.moveToNext();
				}

			}
		} else {
			mNowPlaying.add(mDefaultTrack);
			mNowPlaying.add(mDefaultTrack);
			mNowPlaying.add(mDefaultTrack);
		}

	}

	private void setupDummyCursor() {

		MatrixCursor dummyCursor = new MatrixCursor(new String[] { "item",
				"_id" });

		dummyCursor.addRow(new Object[] { "Advanced Remote", 0 });
		activeCursor = dummyCursor;
	}

	public void setupTrackCursor() {

		/*
		 * final String[] tracks_cursor_cols = { MediaStore.Audio.Media.TITLE,
		 * MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ARTIST,
		 * MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
		 * MediaStore.Audio.Media.DURATION };
		 */

		String[] tracks_cursor_cols = { MediaStore.Audio.Media.TITLE,
				MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ARTIST,
				MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
				MediaStore.Audio.Media.DURATION };

		closeActiveCursor();

		if (!mPlayInApp) {
			setupDummyCursor();
			return;
		}

		Uri tracksUri = null;
		String where = MediaStore.Audio.Media.IS_MUSIC + "=1";

		if (!activeTrack.equals("*")) {
			tracksUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			where = where + " AND " + MediaStore.Audio.Media._ID + " = "
					+ String.valueOf(activeTrackId);
		} else {
			if (!activePlaylist.equals("*")) {
				tracksUri = MediaStore.Audio.Playlists.Members.getContentUri(
						"external", activePlaylistId);
				tracks_cursor_cols[1] = MediaStore.Audio.Playlists.Members.AUDIO_ID;
				activeCategory = Category.Playlist;
			} else if (!activeGenre.equals("*")) {
				tracksUri = MediaStore.Audio.Genres.Members.getContentUri(
						"external", activeGenreId);
				tracks_cursor_cols[1] = MediaStore.Audio.Genres.Members.AUDIO_ID;
				activeCategory = Category.Genre;
			} else
				tracksUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

			if (!activeArtist.equals("*"))
				where = where + " AND " + MediaStore.Audio.Media.ARTIST_ID
						+ " = " + String.valueOf(activeArtistId);

			if (!activeAlbum.equals("*"))
				where = where + " AND " + MediaStore.Audio.Media.ALBUM_ID
						+ " = " + String.valueOf(activeAlbumId);

		}

		activeCursor = mContentResolver.query(tracksUri, tracks_cursor_cols,
				where, null, null);
	}

	private void setupAlbumCursor() {

		closeActiveCursor();

		if (!mPlayInApp) {
			setupDummyCursor();
			return;
		}

		String[] album_cursor_cols = {
				" DISTINCT " + MediaStore.Audio.Media.ALBUM,
				MediaStore.Audio.Media.ALBUM_ID };

		Uri albumsUri = null;
		String where = MediaStore.Audio.Media.IS_MUSIC + "=1";

		if (!activePlaylist.equals("*"))
			albumsUri = MediaStore.Audio.Playlists.Members.getContentUri(
					"external", activePlaylistId);
		else if (!activeGenre.equals("*"))
			albumsUri = MediaStore.Audio.Genres.Members.getContentUri(
					"external", activeGenreId);

		if (albumsUri == null) {
			if (activeArtist.equals("*")) {
				where = null;
				albumsUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
				album_cursor_cols = new String[] {
						MediaStore.Audio.Albums.ALBUM,
						MediaStore.Audio.Albums._ID };
			} else {
				where = null;
				albumsUri = MediaStore.Audio.Artists.Albums.getContentUri(
						"external", activeArtistId);
				album_cursor_cols = new String[] {
						MediaStore.Audio.Artists.Albums.ALBUM,
						MediaStore.Audio.Albums._ID };
			}
		} else {
			if (!activeArtist.equals("*"))
				where = where + " AND " + MediaStore.Audio.Media.ARTIST_ID
						+ " = " + String.valueOf(activeArtistId);
		}

		activeCursor = mContentResolver.query(albumsUri, album_cursor_cols,
				where, null, null);
	}

	private void setupArtistCursor() {

		closeActiveCursor();

		if (!mPlayInApp) {
			setupDummyCursor();
			return;
		}

		String[] artists_cursor_cols = {
				" DISTINCT " + MediaStore.Audio.Media.ARTIST,
				MediaStore.Audio.Media.ARTIST_ID };

		Uri artistsUri = null;
		String where = MediaStore.Audio.Media.IS_MUSIC + "=1";

		if (!activePlaylist.equals("*"))
			artistsUri = MediaStore.Audio.Playlists.Members.getContentUri(
					"external", activePlaylistId);
		else if (!activeGenre.equals("*"))
			artistsUri = MediaStore.Audio.Genres.Members.getContentUri(
					"external", activeGenreId);
		else {
			where = null;
			artists_cursor_cols = new String[] {
					MediaStore.Audio.Artists.ARTIST,
					MediaStore.Audio.Artists._ID };
			artistsUri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
		}

		activeCursor = mContentResolver.query(artistsUri, artists_cursor_cols,
				where, null, null);

	}

	private void setupGenreCursor() {

		closeActiveCursor();

		if (!mPlayInApp) {
			setupDummyCursor();
			return;
		}

		final String[] genre_cursor_cols = { MediaStore.Audio.Genres.NAME,
				MediaStore.Audio.Genres._ID };
		activeCursor = null;
		if (activePlaylist.equals("*")) {
			final Uri genreUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;

			activeCursor = mContentResolver.query(genreUri, genre_cursor_cols,
					null, null, null);
		}
	}

	private void setupPlaylistCursor() {

		closeActiveCursor();

		if (!mPlayInApp) {
			setupDummyCursor();
			return;
		}

		final String[] playlists_cursor_cols = {
				MediaStore.Audio.Playlists.NAME, MediaStore.Audio.Playlists._ID };
		final Uri playlistUri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;

		activeCursor = mContentResolver.query(playlistUri,
				playlists_cursor_cols, null, null, null);

	}

}
