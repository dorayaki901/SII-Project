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
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ToyVpnService extends VpnService implements Handler.Callback, Runnable {
	private static final String TAG = "VpnService";

	private String mServerAddress;
	private int mServerPort = 1050;
	public static final int MAX_PACKET_LENGTH = 64000;
	private PendingIntent mConfigureIntent;
	private HashMap<IdentifierKeyThread, InfoThread> mThreadMap;
	private Thread mThread;
	private ParcelFileDescriptor mInterface;
	//a. Configure a builder for the interface.
	Builder builder = new Builder();
	int count=0;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// The handler is only used to show messages.
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
			//TODO timer 
			int timer = 0;
			int length = 0;
			boolean idle;
			
			IdentifierKeyThread key = new IdentifierKeyThread();
			ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_LENGTH);
			//a. Configure the TUN and get the interface.
			ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue = new ConcurrentLinkedQueue<ByteBuffer>();

			mInterface = builder.setSession("MyVPNService")
					.addAddress("10.0.0.2",32)     	
					.addRoute("0.0.0.0",0)
					/*.addDnsServer("8.8.8.8")*/.establish();
			if(mInterface == null) 
				Log.i(TAG,"error: vpn not prepared");
			FileInputStream in = new FileInputStream(
					mInterface.getFileDescriptor());

			FileOutputStream out = new FileOutputStream(
					mInterface.getFileDescriptor());
			Packet appPacket = new Packet(packet);
			int threadNum = 0;
			/****/
			SendToApp writeThread = new SendToApp(out,sentoToAppQueue);
			Thread sendToApp = new Thread(writeThread);
			sendToApp.start();
			/****/
			while (true) {
				//idle = true;
				// Read the outgoing packet from the input stream.
				length = in.read(packet.array());      
				if (length > 0) {
					
					appPacket = new Packet(packet);

					if(!appPacket.ip4Header.destinationAddress.getHostAddress().equals("160.80.10.11")){
						packet.clear();
						continue;
					}
					// Check if a thread is already manage the connection 
					key.set(appPacket);
					InfoThread info = mThreadMap.get(key);

					if (info != null){ // Thread is mapped
						if(info.mThread.isAlive()){
							Log.i(TAG,"Manager thread already exist");
							try{
								ByteBuffer b = ByteBuffer.allocate(4);
								b.putInt(length);
								b.position(0);
								//Log.i(TAG,"writing app packet on Pipe: "+ length + b.getInt());
								//First, write the pkt len, then the real pkt
								info.mPipeOutputStream.write(b.array(),0,4);
								info.mPipeOutputStream.flush();
								info.mPipeOutputStream.write(packet.array(),0,length);
								info.mPipeOutputStream.flush();

							} catch (IOException e) {
								e.printStackTrace();
								mThreadMap.remove(info);
							}
							continue;
						}
						else {
							Log.i(TAG,"Thread is dead--TID:" + mThread.getId());
							mThreadMap.remove(info);
						}

					}

					// New Connection
					//Log.i(TAG,"Creation new manager thread");
					ThreadLog newThread = new ThreadLog(out, packet, length, this,threadNum,sentoToAppQueue);
					Thread logPacket = new Thread(newThread);

					PipedInputStream readPipe = new PipedInputStream(MAX_PACKET_LENGTH);
					PipedOutputStream writePipe = new PipedOutputStream(readPipe);

					InfoThread infoThread=new InfoThread(logPacket, writePipe);

					newThread.setPipe(readPipe);

					mThreadMap.put(key, infoThread); 

					logPacket.start();

					threadNum++;

					//idle = false;

					packet.clear();

				}
				Thread.sleep(100);
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
