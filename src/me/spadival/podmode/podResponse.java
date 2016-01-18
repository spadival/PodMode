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

import java.util.Calendar;

import android.util.Log;

class ExtraInfo {
	int noOfcmdTypes;
	byte[] cmdTypes;

}

public class podResponse {
	byte[] resBytes;
	ExtraInfo devInfo;
	byte devId;
	byte devOptions;
	String accessoryName = "";
	String accessoryMnf = "";
	String accessoryModel = "";

	public podResponse(podCommand pCommand, byte[] response) {
		int respLen = 0;

		switch (pCommand.mode) {
		case 0:
			switch (response[0]) {

			case (byte) 0x00:
				respLen = 6;

				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x02;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x00;
				break;

			case (byte) 0x02:
				respLen = 8;

				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x04;
				resBytes[3] = (byte) pCommand.mode;
				resBytes[4] = (byte) 0x02;
				if (response.length == 2)
					resBytes[5] = (byte) response[1];
				else
					resBytes[5] = (byte) 0x00;
				resBytes[6] = (byte) pCommand.rawBytes[4];
				break;

			case (byte) 0x0A:
				respLen = 9;

				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x05;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x0A;
				resBytes[5] = (byte) 0x03;
				resBytes[6] = (byte) 0x00;
				resBytes[7] = (byte) 0x00;
				break;

			case (byte) 0x0C:
				respLen = 18;

				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x0E;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x0C;
				resBytes[5] = (byte) 0x41;
				resBytes[6] = (byte) 0x4B;
				resBytes[7] = (byte) 0x30;
				resBytes[8] = (byte) 0x31;
				resBytes[9] = (byte) 0x35;
				resBytes[10] = (byte) 0x4E;
				resBytes[11] = (byte) 0x48;
				resBytes[12] = (byte) 0x37;
				resBytes[13] = (byte) 0x5A;
				resBytes[14] = (byte) 0x33;
				resBytes[15] = (byte) 0x39;
				resBytes[16] = (byte) 0x00;

				break;

			case (byte) 0x0E:
				respLen = 18;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x0E;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x0E;
				resBytes[5] = (byte) 0x00;
				resBytes[6] = (byte) 0x0B;
				resBytes[7] = (byte) 0x00;
				resBytes[8] = (byte) 0x05;
				resBytes[9] = (byte) 0x50;
				resBytes[10] = (byte) 0x41;
				resBytes[11] = (byte) 0x31;
				resBytes[12] = (byte) 0x34;
				resBytes[13] = (byte) 0x37;
				resBytes[14] = (byte) 0x4C;
				resBytes[15] = (byte) 0x4C;
				resBytes[16] = (byte) 0x00;
				break;

			case (byte) 0x0F:
				respLen = 9;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x05;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x10;
				resBytes[5] = (byte) 0x00;
				resBytes[6] = (byte) 0x01;
				resBytes[7] = (byte) 0x02;
				break;

			case (byte) 0x14:
				respLen = 6;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x02;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x14;
				break;

			case (byte) 0x16:
				respLen = 7;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x03;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x16;
				if (response.length == 2)
					resBytes[5] = (byte) response[1];
				else
					resBytes[5] = (byte) 0x00;
				break;

			case (byte) 0x17:
				if (response[1] == 0x01) {
					respLen = 23;
					resBytes = new byte[respLen];
					resBytes[2] = (byte) 0x13;
					resBytes[3] = (byte) 0x00;
					resBytes[4] = (byte) 0x17;
					resBytes[5] = (byte) 0x30;
					resBytes[6] = (byte) 0x01;
					resBytes[7] = (byte) 0x10;
					resBytes[8] = (byte) 0x33;
					resBytes[9] = (byte) 0x04;
					resBytes[10] = (byte) 0x05;
					resBytes[11] = (byte) 0x66;
					resBytes[12] = (byte) 0x09;
					resBytes[13] = (byte) 0x12;
					resBytes[14] = (byte) 0x88;
					resBytes[15] = (byte) 0x50;
					resBytes[16] = (byte) 0x00;
					resBytes[17] = (byte) 0x03;
					resBytes[18] = (byte) 0x11;
					resBytes[19] = (byte) 0x55;
					resBytes[20] = (byte) 0x01;
					resBytes[21] = (byte) 0x00;
				} else {
					respLen = 27;
					resBytes = new byte[respLen];
					resBytes[2] = (byte) 0x17;
					resBytes[3] = (byte) 0x00;
					resBytes[4] = (byte) 0x17;
					resBytes[5] = (byte) 0x30;
					resBytes[6] = (byte) 0x01;
					resBytes[7] = (byte) 0x10;
					resBytes[8] = (byte) 0x33;
					resBytes[9] = (byte) 0x04;
					resBytes[10] = (byte) 0x05;
					resBytes[11] = (byte) 0x66;
					resBytes[12] = (byte) 0x09;
					resBytes[13] = (byte) 0x12;
					resBytes[14] = (byte) 0x88;
					resBytes[15] = (byte) 0x50;
					resBytes[16] = (byte) 0x00;
					resBytes[17] = (byte) 0x03;
					resBytes[18] = (byte) 0x11;
					resBytes[19] = (byte) 0x55;
					resBytes[20] = (byte) 0x01;
					resBytes[21] = (byte) 0x00;
					resBytes[22] = (byte) 0x11;
					resBytes[23] = (byte) 0x55;
					resBytes[24] = (byte) 0x01;
					resBytes[25] = (byte) 0x00;
				}
				break;

			case (byte) 0x19:
				respLen = 7;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x03;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x19;
				resBytes[5] = (byte) 0x00;
				break;

			case (byte) 0x25:
				respLen = 14;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x0A;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x25;
				resBytes[5] = (byte) 0x00;
				resBytes[6] = (byte) 0x00;
				resBytes[7] = (byte) 0x00;
				resBytes[8] = (byte) 0x00;
				resBytes[9] = (byte) 0x00;
				resBytes[10] = (byte) 0x00;
				resBytes[11] = (byte) 0x00;
				resBytes[12] = (byte) 0x00;
				break;

			case (byte) 0x27:
				// respLen = 14;
				// resBytes = new byte[respLen];
				// resBytes[2] = (byte) 0x0A;
				// resBytes[3] = (byte) 0x00;
				// resBytes[4] = (byte) 0x27;
				// resBytes[5] = (byte) 0x02;
				// resBytes[6] = (byte) 0x00;
				// resBytes[7] = (byte) 0x1B;
				// resBytes[8] = (byte) 0x01;
				// resBytes[9] = (byte) 0x02;
				// resBytes[10] = (byte) 0x03;
				// resBytes[11] = (byte) 0x00;
				// resBytes[12] = (byte) 0x00;

				respLen = 7;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x03;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x27;
				resBytes[5] = (byte) 0x00;

				break;

			case (byte) 0x3A:
				byte[] buildBytes = new byte[400];
				buildBytes[0] = (byte) 0x00;
				buildBytes[1] = (byte) 0x3A;
				buildBytes[2] = (byte) pCommand.rawBytes[5];

				int srcIdx = 0;
				int destIdx = 3;
				int tokenCount = 0;

				int numTokens = (int) (pCommand.rawBytes[5] & 0xFF);

				byte[] tokens = new byte[pCommand.length - 3];

				System.arraycopy(pCommand.rawBytes, 6, tokens, 0,
						pCommand.length - 3);

				while (srcIdx < tokens.length) {

					int tokenLen = (int) (tokens[srcIdx] & 0xFF);

					if (tokenLen == 0) {
						Log.d("PodMode", "podResponse token error");
						break;
					}

					if (tokens[srcIdx + 1] == (byte) 0x00
							&& tokens[srcIdx + 2] == (byte) 0x00) {
						tokenCount++;
						buildBytes[destIdx++] = (byte) 0x03;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = (byte) 0x00;
					}

					if (tokens[srcIdx + 1] == (byte) 0x00
							&& tokens[srcIdx + 2] == (byte) 0x01) {
						tokenCount++;
						buildBytes[destIdx++] = (byte) 0x03;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = (byte) 0x01;
						buildBytes[destIdx++] = (byte) 0x00;
					}

					if (tokens[srcIdx + 1] == (byte) 0x00
							&& tokens[srcIdx + 2] == (byte) 0x02) {
						tokenCount++;
						buildBytes[destIdx++] = (byte) 0x04;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = (byte) 0x02;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = tokens[srcIdx + 3];

						int i;

						switch (tokens[srcIdx + 3]) {
						case 0x01:
							for (i = 0; tokens[i + srcIdx + 4] != 0x00
									&& i < 64; i++) {

							}

							if (i > 0) {
								byte[] nameBytes = new byte[i];

								System.arraycopy(tokens, srcIdx + 4, nameBytes,
										0, i);

								accessoryName = new String(nameBytes);
							}
							break;
						case 0x06:
							for (i = 0; tokens[i + srcIdx + 4] != 0x00
									&& i < 64; i++) {

							}

							if (i > 0) {
								byte[] nameBytes = new byte[i];

								System.arraycopy(tokens, srcIdx + 4, nameBytes,
										0, i);
								accessoryMnf = new String(nameBytes);

							}
							break;
						case 0x07:
							for (i = 0; tokens[i + srcIdx + 4] != 0x00
									&& i < 64; i++) {

							}

							if (i > 0) {
								byte[] nameBytes = new byte[i];

								System.arraycopy(tokens, srcIdx + 4, nameBytes,
										0, i);
								accessoryModel = new String(nameBytes);

							}
							break;
						}
					}

					if (tokens[srcIdx + 1] == (byte) 0x00
							&& tokens[srcIdx + 2] == (byte) 0x03) {
						tokenCount++;
						buildBytes[destIdx++] = (byte) 0x04;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = (byte) 0x03;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = tokens[srcIdx + 3];
					}

					if (tokens[srcIdx + 1] == (byte) 0x00
							&& tokens[srcIdx + 2] == (byte) 0x04) {
						tokenCount++;
						buildBytes[destIdx++] = (byte) 0x04;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = (byte) 0x04;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = tokens[srcIdx + 3];

					}

					if (tokens[srcIdx + 1] == (byte) 0x00
							&& tokens[srcIdx + 2] == (byte) 0x05) {
						tokenCount++;
						buildBytes[destIdx++] = (byte) 0x03;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = (byte) 0x05;
						buildBytes[destIdx++] = (byte) 0x00;
					}

					if (tokens[srcIdx + 1] == (byte) 0x01
							&& tokens[srcIdx + 2] == (byte) 0x00) {
						tokenCount++;
						buildBytes[destIdx++] = (byte) 0x03;
						buildBytes[destIdx++] = (byte) 0x01;
						buildBytes[destIdx++] = (byte) 0x00;
						buildBytes[destIdx++] = (byte) 0x00;
					}

					srcIdx += tokenLen + 1;
				}

				if (numTokens != tokenCount)
					Log.d("PodMode",
							"podResponse token mismatch "
									+ String.valueOf(tokenCount));

				respLen = destIdx + 4;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) destIdx;
				System.arraycopy(buildBytes, 0, resBytes, 3, destIdx);
				break;

			case (byte) 0x3C:
				respLen = 7;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x03;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x3C;
				resBytes[5] = (byte) 0x00;
				break;

			case (byte) 0x3F:
				respLen = 9;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x05;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x3F;
				resBytes[5] = (byte) 0x77;
				resBytes[6] = (byte) 0x77;
				resBytes[7] = (byte) 0x01;
				break;

			case (byte) 0x4C:
				respLen = 15;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x0B;
				resBytes[3] = (byte) 0x00;
				resBytes[4] = (byte) 0x4C;
				resBytes[5] = (byte) 0x00;
				resBytes[6] = (byte) 0x00;
				resBytes[7] = (byte) 0x00;
				resBytes[8] = (byte) 0x00;
				resBytes[9] = (byte) 0x03;
				resBytes[10] = (byte) 0x3D;
				resBytes[11] = (byte) 0xFF;
				resBytes[12] = (byte) 0x73;
				resBytes[13] = (byte) 0xFF;
				break;
			default:
				respLen = 5 + response.length;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) (response.length + 1);
				resBytes[3] = (byte) pCommand.mode;
				System.arraycopy(response, 0, resBytes, 4, response.length);
				break;
			}
			break;
		case 1:
			break;

		case 2:
			respLen = 5 + response.length;
			resBytes = new byte[respLen];
			resBytes[2] = (byte) (response.length + 1);
			resBytes[3] = (byte) pCommand.mode;
			System.arraycopy(response, 0, resBytes, 4, response.length);
			break;
		case 3:
			switch (response[0]) {

			case (byte) 0x02:
				respLen = 8;

				resBytes = new byte[respLen];
				resBytes[3] = (byte) pCommand.mode;
				resBytes[4] = (byte) 0x02;
				resBytes[2] = (byte) 0x04;
				resBytes[5] = (byte) 0x00;
				resBytes[6] = (byte) pCommand.rawBytes[4];
				break;

			case (byte) 0x0D:
				switch (pCommand.rawBytes[5]) {
				case (byte) 0x09:
					respLen = 13;
					resBytes = new byte[respLen];
					resBytes[2] = (byte) 0x09;
					resBytes[3] = (byte) 0x03;
					resBytes[4] = (byte) 0x0D;
					resBytes[5] = (byte) 0x09;

					Calendar cal = Calendar.getInstance();

					short currentYear = (short) cal.get(Calendar.YEAR);
					resBytes[6] = (byte) ((currentYear >> 8) & 0xff); // CC
					resBytes[7] = (byte) (currentYear & 0xff); // YY
					resBytes[8] = (byte) ((cal.get(Calendar.MONTH) & 0xff) + 1); // MT
					resBytes[9] = (byte) (cal.get(Calendar.DAY_OF_MONTH) & 0xff); // DD
					resBytes[10] = (byte) (cal.get(Calendar.HOUR_OF_DAY) & 0xff); // HH
					resBytes[11] = (byte) (cal.get(Calendar.MINUTE) & 0xff); // MM
					break;
				}
				break;
			}
			break;

		case 4:
			switch (response[1]) {

			case (byte) 0x01:
				respLen = 10;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x06;
				resBytes[3] = (byte) 0x04;
				resBytes[4] = (byte) 0x00;
				resBytes[5] = (byte) 0x01;
				resBytes[6] = (byte) 0x00;
				resBytes[7] = (byte) pCommand.rawBytes[4];
				resBytes[8] = (byte) pCommand.rawBytes[5];
				break;

			case (byte) 0x0A:
				respLen = 8;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x04;
				resBytes[3] = (byte) 0x04;
				resBytes[4] = (byte) 0x00;
				resBytes[5] = (byte) 0x0A;
				resBytes[6] = (byte) response[2];
				break;

			case (byte) 0x34:
				respLen = 12;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) 0x08;
				resBytes[3] = (byte) 0x04;
				resBytes[4] = (byte) 0x00;
				resBytes[5] = (byte) 0x34;
				resBytes[6] = (byte) 0x01;
				resBytes[7] = (byte) 0xE0;
				resBytes[8] = (byte) 0x01;
				resBytes[9] = (byte) 0x40;
				resBytes[10] = (byte) 0x01;
				break;

			default:
				respLen = 5 + response.length;
				resBytes = new byte[respLen];
				resBytes[2] = (byte) (response.length + 1);
				resBytes[3] = (byte) pCommand.mode;
				System.arraycopy(response, 0, resBytes, 4, response.length);
				break;
			}

		}

		resBytes[0] = (byte) 0xFF;
		resBytes[1] = (byte) 0x55;

		short checkSum = 0;

		for (int i = 2; i < respLen; i++)
			checkSum += (short) resBytes[i];

		checkSum = (short) (0x100 - checkSum);

		resBytes[respLen - 1] = (byte) (checkSum & 0xFF);

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < resBytes.length; i++) {
			sb.append(String.format("%02X ", resBytes[i]));
		}

		Log.d("PodMode", "PodR     : " + sb.toString());

	}

	public byte[] getBytes() {
		return resBytes;
	}
}
