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

	public static final int MAX_PACKET_LENGTH = 94000;
	private HashMap<Integer, InfoThread> mThreadMap;
	private Thread mThread;
	private ParcelFileDescriptor mInterface;
	//a. Configure a builder for the interface.
	Builder builder = new Builder();
	int count=0;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// The handler is only used to show messages.
		mThread = new Thread(this, "ToyVpnThread");
		mThreadMap = new HashMap<Integer, InfoThread>();
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

			Integer key;
			ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_LENGTH);
			//a. Configure the TUN and get the interface.
			ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue = new ConcurrentLinkedQueue<ByteBuffer>();

			mInterface = builder.setSession("MyVPNService")
					//					.setMtu(70000)
					.addAddress("10.0.0.2",32)     	
					.addRoute("0.0.0.0",0)
					/*.addDnsServer("8.8.8.8")*/.establish();
			if(mInterface == null) 
				Log.i(TAG,"error: vpn not prepared");
			FileInputStream in = new FileInputStream(
					mInterface.getFileDescriptor());

			FileOutputStream out = new FileOutputStream(
					mInterface.getFileDescriptor());
			Packet appPacket;

			/****/
			SendToApp writeThread = new SendToApp(out,sentoToAppQueue);
			Thread sendToApp = new Thread(writeThread);
			sendToApp.start();
			/****/

			ByteBuffer b = ByteBuffer.allocate(4);


			while (true) {

				length = in.read(packet.array());      

				if (length > 0) { 

					appPacket = new Packet(packet,length);

					if(!appPacket.isTCP() && !appPacket.isUDP()){
						packet.clear();
						continue;
					}
					
					/***************************************/
//					String appHostAddress=appPacket.ip4Header.destinationAddress.getHostAddress().substring(0, 3);
//					if(!appHostAddress.equals("54.") && !appHostAddress.equals("192") ){
//						packet.clear();
//						continue;
//					}
					/***********************************/	

					if(!appPacket.isTCP()){ // E' UDP
						ThreadLog newThread = new ThreadLog(out, packet, length, this, sentoToAppQueue);
						Thread logPacket = new Thread(newThread);
						logPacket.start();
						packet.clear();
						continue;
					}

					// IF IT ARRIVE HERE IS TCP PKT
					if((appPacket.payloadLen)<=0){
						//Pure ACK is dropped
						if (!(appPacket.tcpHeader.isACK() && !appPacket.tcpHeader.isFIN() &&  !appPacket.tcpHeader.isRST()) ){
							ThreadLog newThread = new ThreadLog(out, packet, length, this, sentoToAppQueue);
							Thread logPacket = new Thread(newThread);
							logPacket.start();
						}
							packet.clear();
							continue;

					}
					else{
						//Check if a manager thread already exist
						key = (IdentifierKeyThread.hashCode(appPacket));
						InfoThread info = mThreadMap.get(key);

						if (info != null){ // Thread is mapped
							if(info.mThread.isAlive()){				
								try{

									b.putInt(length);
									b.position(0);

									//First, write the pkt len, then the real pkt
									info.mPipeOutputStream.write(b.array(),0,4);
									info.mPipeOutputStream.flush();

									info.mPipeOutputStream.write(packet.array(),0,length);
									info.mPipeOutputStream.flush();

								} catch (IOException e) {
									e.printStackTrace();
									mThreadMap.remove(info);
									break;

								}
								packet.clear();
								continue;
							}
							else {
								//									Log.i(TAG,"Thread is dead--TID:" + mThread.getId());
								mThreadMap.remove(info);
							}

						}
					}

					// New Connection

					ThreadLog newThread = new ThreadLog(out, packet, length, this, sentoToAppQueue);
					Thread logPacket = new Thread(newThread);

					//If Pkt is not fragmented
					if(!appPacket.tcpHeader.isPSH() && appPacket.tcpHeader.isACK()){ 
						PipedInputStream readPipe = new PipedInputStream(MAX_PACKET_LENGTH);
						PipedOutputStream writePipe = new PipedOutputStream(readPipe);

						InfoThread infoThread=new InfoThread(logPacket, writePipe);
						newThread.setPipe(readPipe);
						mThreadMap.put(key, infoThread); 
					}

					logPacket.start();

				}
				packet.clear();
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
