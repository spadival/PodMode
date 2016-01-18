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

import android.util.Log;


enum podCmd {
	AppCmd, AppAck, unknownCmd, StartID, EndID, GetPodProtocols, GetPodOptions, GetPodOption, GetProtoVersion, GetSoftVersion, GetSerialNum, SetIdTokens, DeviceDetails, DeviceAuthInfo, DeviceAuthSig, ReqAdvRemote, SwitchRemote, RemoteNotify, StateInfo, RemoteButtonRel, RemotePlayPause, RemoteVolPlus, RemoteVolMinus, RemoteSkipFwd, RemoteSkipRwd, RemoteNextAlb, RemotePrevAlb, RemoteStop, RemoteJustPlay, RemoteJustPause, RemoteMuteToggle, RemoteNextPlay, RemotePrevPlay, RemoteShuffleToggle, RemoteRepeatToggle, RemoteOff, RemoteOn, RemoteMenu, RemoteOk, RemoteScrollUp, RemoteScrollDn, RemoteRelease, SwitchAdvanced, StartAdvRemote, EndAdvRemote, DevModel, DevTypeSize, DevName, SwitchToMainPlaylist, SwitchToItem, GetCountForType, GetItemNames, GetTimeStatus, GetPlaylistPos, GetSongTitle, GetSongArtist, GetSongAlbum, PollingMode, ExecPlaylist, PlaybackControl, GetShuffleMode, SetShuffleMode, GetRepeatMode, SetRepeatMode, PicBlock, GetScreenSize, GetPlayListSongNum, JumpToSong, GetUpdateFlag, SetUpdateFlag,FrankPlaylist
};

public class podCommand {
	byte rawBytes[];
	int length;
	int mode;
	short hexCmd;
	byte params[];
	byte checksum;
	podCmd command;

	public podCommand(byte[] cmd, int len) {
		rawBytes = new byte[len];
		System.arraycopy(cmd, 0, rawBytes, 0, len);
		command = podCmd.unknownCmd;
		length = (int) (cmd[2] & 0xFF);
		mode = (int) (cmd[3] & 0xFF);

		hexCmd = (short) ((cmd[4] << 8) + (cmd[5] & 0xFF));

		if (length > 3) {
			params = new byte[length - 3];

			for (int i = 0; i < length - 3; i++)
				params[i] = cmd[i + 6];
		} else
			params = new byte[] { 0 };

		checksum = cmd[length + 3];

		if (mode == 0x00) {
			if (hexCmd == 0x0104)
				command = podCmd.SwitchAdvanced;
			if (hexCmd == 0x0102)
				command = podCmd.SwitchRemote;
			if (cmd[4] == 0x03)
				command = podCmd.ReqAdvRemote;
			if (cmd[4] == 0x05)
				command = podCmd.StartAdvRemote;
			if (cmd[4] == 0x06)
				command = podCmd.EndAdvRemote;
			if (cmd[4] == 0x09)
				command = podCmd.GetSoftVersion;
			if (cmd[4] == 0x0B)
				command = podCmd.GetSerialNum;
			if (cmd[4] == 0x0D)
				command = podCmd.DevModel;
			if (cmd[4] == 0x38)
				command = podCmd.StartID;
			if (cmd[4] == 0x13)
				command = podCmd.GetPodProtocols;
			if (cmd[4] == 0x0F)
				command = podCmd.GetProtoVersion;
			if (cmd[4] == 0x4B)
				command = podCmd.GetPodOptions;
			if (cmd[4] == 0x39)
				command = podCmd.SetIdTokens;
			if (cmd[4] == 0x3B)
				command = podCmd.EndID;
			if (cmd[4] == 0x28)
				command = podCmd.DeviceDetails;
			if (cmd[4] == 0x15)
				command = podCmd.DeviceAuthInfo;
			if (cmd[4] == 0x18)
				command = podCmd.DeviceAuthSig;
			if (cmd[4] == 0x24)
				command = podCmd.GetPodOption;
			if (cmd[4] == 0x41)
				command = podCmd.AppAck;
			if (cmd[4] == 0x64)
				command = podCmd.AppCmd;
		}

		if (mode == 0x02) {
			if (length == 3) {
				switch (hexCmd) {
				case 0x0000:
					command = podCmd.RemoteButtonRel;
					break;
				case 0x0001:
					command = podCmd.RemotePlayPause;
					break;
				case 0x0002:
					command = podCmd.RemoteVolPlus;
					break;
				case 0x0004:
					command = podCmd.RemoteVolMinus;
					break;
				case 0x0008:
					command = podCmd.RemoteSkipFwd;
					break;
				case 0x0010:
					command = podCmd.RemoteSkipRwd;
					break;
				case 0x0020:
					command = podCmd.RemoteNextAlb;
					break;
				case 0x0040:
					command = podCmd.RemotePrevAlb;
					break;
				case 0x0080:
					command = podCmd.RemoteStop;
					break;
				}
			}

			if (length == 4 && hexCmd == 0x0000) {
				switch (params[0]) {
				case (byte) 0x01:
					command = podCmd.RemoteJustPlay;
					break;
				case (byte) 0x02:
					command = podCmd.RemoteJustPause;
					break;
				case (byte) 0x04:
					command = podCmd.RemoteMuteToggle;
					break;
				case (byte) 0x20:
					command = podCmd.RemoteNextPlay;
					break;
				case (byte) 0x40:
					command = podCmd.RemotePrevPlay;
					break;
				case (byte) 0x80:
					command = podCmd.RemoteShuffleToggle;
					break;
				}
			}

			if (length == 5 && hexCmd == 0x0000 && params[0] == (byte) 0x00) {
				switch (params[1]) {
				case (byte) 0x01:
					command = podCmd.RemoteRepeatToggle;
					break;
				case (byte) 0x04:
					command = podCmd.RemoteOff;
					break;
				case (byte) 0x08:
					command = podCmd.RemoteOn;
					break;
				case (byte) 0x40:
					command = podCmd.RemoteMenu;
					break;
				case (byte) 0x80:
					command = podCmd.RemoteOk;
					break;
				}
			}

			if (length == 6 && hexCmd == 0x0000 && params[0] == (byte) 0x00
					&& params[1] == (byte) 0x00) {
				switch (params[2]) {
				case (byte) 0x00:
					command = podCmd.RemoteRelease;
					break;
				case (byte) 0x01:
					command = podCmd.RemoteScrollUp;
					break;
				case (byte) 0x02:
					command = podCmd.RemoteScrollDn;
					break;

				}
			}
		}

		if (mode == 0x03) {
			if (cmd[4] == 0x08)
				command = podCmd.RemoteNotify;
			if (cmd[4] == 0x0C)
				command = podCmd.StateInfo;

		}

		if (mode == 0x04) {
			switch (hexCmd) {
			case 0x0009:
				command = podCmd.GetUpdateFlag;
				break;
			case 0x000B:
				command = podCmd.SetUpdateFlag;
				break;
			case 0x0012:
				command = podCmd.DevTypeSize;
				break;
			case 0x0014:
				command = podCmd.DevName;
				break;
			case 0x0016:
				command = podCmd.SwitchToMainPlaylist;
				break;
			case 0x0017:
				command = podCmd.SwitchToItem;
				break;
			case 0x0018:
				command = podCmd.GetCountForType;
				break;
			case 0x001A:
				command = podCmd.GetItemNames;
				break;
			case 0x001C:
				command = podCmd.GetTimeStatus;
				break;
			case 0x001E:
				command = podCmd.GetPlaylistPos;
				break;
			case 0x0020:
				command = podCmd.GetSongTitle;
				break;
			case 0x0022:
				command = podCmd.GetSongArtist;
				break;
			case 0x0024:
				command = podCmd.GetSongAlbum;
				break;
			case 0x0026:
				command = podCmd.PollingMode;
				break;
			case 0x0028:
				command = podCmd.ExecPlaylist;
				break;
			case 0x0029:
				command = podCmd.PlaybackControl;
				break;
			case 0x002C:
				command = podCmd.GetShuffleMode;
				break;
			case 0x002E:
				command = podCmd.SetShuffleMode;
				break;
			case 0x002F:
				command = podCmd.GetRepeatMode;
				break;
			case 0x0031:
				command = podCmd.SetRepeatMode;
				break;
			case 0x0032:
				command = podCmd.PicBlock;
				break;
			case 0x0033:
				command = podCmd.GetScreenSize;
				break;
			case 0x0035:
				command = podCmd.GetPlayListSongNum;
				break;
			case 0x0037:
				command = podCmd.JumpToSong;
				break;
			case 0x004E:
				command = podCmd.FrankPlaylist;
				break;
			}
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < rawBytes.length; i++) {
			sb.append(String.format("%02X ", rawBytes[i]));
		}

		if (command == podCmd.unknownCmd)
			Log.d("PodMode", "PodC TBD : " + sb.toString());
		else
			Log.d("PodMode", "PodC     : " + sb.toString() + " " + command.name()); 

	}
}
