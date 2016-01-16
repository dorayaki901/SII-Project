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

package com.si.android.vpnproxy;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.net.VpnService.Builder;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.LogPrinter;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Enumeration;
import java.util.HashMap;

import org.apache.http.conn.util.InetAddressUtils;

public class ToyVpnService extends VpnService implements Handler.Callback, Runnable {
	private static final String TAG = "ToyVpnService";

	private String mServerAddress;
	private int mServerPort = 1050;
	public static final int MAX_PACKET_LENGTH = 1600;
	private PendingIntent mConfigureIntent;
	private HashMap<IdentifierKeyThread, InfoThread> mThreadMap;
	private Thread mThread;
	private ParcelFileDescriptor mInterface;
	//a. Configure a builder for the interface.
	Builder builder = new Builder();

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// The handler is only used to show messages.
		Log.i(TAG, "Service Starting");
		mThread = new Thread(this, "ToyVpnThread");
		mThreadMap = new HashMap<IdentifierKeyThread, InfoThread>();
		//start the service
		mThread.start();

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		if (mThread != null) {
			mThread.interrupt();
		}
	}

	@Override
	public boolean handleMessage(Message message) {
		if (message != null) {
			Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
		}
		return true;
	}

	@Override
	public synchronized void run() {
		try {
			//a. Configure the TUN and get the interface.

			mInterface = builder.setSession("MyVPNService")
					//.setMtu(1500)
					.addAddress("10.0.2.0",32)     	
					.addRoute("0.0.0.0",0)
					/*.addDnsServer("8.8.8.8")*/.establish();

			FileInputStream in = new FileInputStream(
					mInterface.getFileDescriptor());

			FileOutputStream out = new FileOutputStream(
					mInterface.getFileDescriptor());

			IdentifierKeyThread key = new IdentifierKeyThread();
			ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_LENGTH);
			int timer = 0;
			int length = 0;
			boolean idle;

			int i = 0;
			while (true) {
				//idle = true;

				// Read the outgoing packet from the input stream.
				length = in.read(packet.array());      
				if (length > 0) {
					// check if a thread is already manage the connection 

					Packet appPacket = new Packet(packet);

					if(appPacket == null){
						Log.i(TAG, "Error packt NULL");
						packet.clear();
						continue;
					}
					if(!appPacket.ip4Header.destinationAddress.getHostAddress().equals("207.244.90.212")){
						packet.clear();
						continue;
					}
					//					ByteBuffer packetBuffer = ByteBuffer.allocate(length);
					//					packetBuffer.put(packet.array(), 0, length);
					//					packetBuffer.position(0);
					//					Packet appPacket = new Packet(packetBuffer);

					//	Log.i(TAG,"PKT CATTURATO!");

						//						Log.i(TAG,"Pre-Existing Connection to " + appPacket.ip4Header.destinationAddress);
						key.set(appPacket);

						InfoThread info = mThreadMap.get(key);

						if (info != null){ // Thread is mapped
							//	Log.i(TAG,"DESTINAZIONE GIA MEMORIZZATA");
							if(info.mThread.isAlive()){
								//	Log.i(TAG,"THREAD ANCORA VIVO");
								Log.i(TAG, "Write Pipe:" + length+": " + appPacket.toString());
								//	try{//TODO devo considerare il fatto che il thread mi può morire tra i due momenti
								//Es per scadenza timeout
							
								ByteBuffer b = ByteBuffer.allocate(4);
								b.putInt(length);
								b.position(0);
								Log.i(TAG,"INVIO PKT SU PIPE: "+ length + b.getInt());
								info.mPipeOutputStream.write(b.array(),0,4);
								info.mPipeOutputStream.flush();
								info.mPipeOutputStream.write(packet.array(),0,length);
								info.mPipeOutputStream.flush();

								//	} catch (IOException e) {
								//		e.printStackTrace();
								//		mThreadMap.remove(info);
								//	}
								continue;
							}
							else {
								//	Log.i(TAG,"THREAD MORTO");
								mThreadMap.remove(info);
							}

						}
					
					// New Connection
					//					Log.i(TAG,"CREAZIONE NUOVO THREAD");
					ThreadLog newThread = new ThreadLog(out, packet, length, this,i);
					Thread logPacket = new Thread(newThread);

					PipedInputStream readPipe = new PipedInputStream(MAX_PACKET_LENGTH);
					PipedOutputStream writePipe = new PipedOutputStream(readPipe);

					InfoThread infoThread=new InfoThread(logPacket, writePipe);

					newThread.setPipe(readPipe);

					mThreadMap.put(key, infoThread); 

					logPacket.start();

					i++;

					//idle = false;

					packet.clear();
				}
				Thread.sleep(1000);
				// Thread.sleep(100);
				// If we are idle or waiting for the network, sleep for a
				// fraction of time to avoid busy looping.
				// if (idle) {
				//	   Thread.sleep(100);
				//     }   
			}
		} catch (Exception e) {
			// Catch any exception
			e.printStackTrace();
		} finally {
			try {
				if (mInterface != null) {
					mInterface.close();
					mInterface = null;
				}
			} catch (Exception e) {

			}
		}
	}

}
