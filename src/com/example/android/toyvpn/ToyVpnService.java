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

package com.example.android.toyvpn;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.net.VpnService.Builder;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

import org.apache.http.conn.util.InetAddressUtils;


public class ToyVpnService extends VpnService implements Handler.Callback, Runnable {
	private static final String TAG = "ToyVpnService";

	private String mServerAddress;
	private int mServerPort = 1050;
	private PendingIntent mConfigureIntent;

	private Thread mThread;
	private Handler mHandler;
	private ParcelFileDescriptor mInterface;
	private String mParameters;
	public byte[] InBuff = new byte[200]; 
	//a. Configure a builder for the interface.
	Builder builder = new Builder();

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// The handler is only used to show messages.
		Log.i("ciao", "onCreate");   
		mThread = new Thread(this, "ToyVpnThread");
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


			ByteBuffer packet = ByteBuffer.allocate(32767);
			int timer = 0;
			int length = 0;
			boolean idle;
			
	
			while (true) {
				//idle = true;

				// Read the outgoing packet from the input stream.
				length = in.read(packet.array());      
				if (length > 0) {
				
					Thread logPacket= new Thread(new ThreadLog(out, packet, this));
					logPacket.start();
					
					packet.clear();

					//idle = false;
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
