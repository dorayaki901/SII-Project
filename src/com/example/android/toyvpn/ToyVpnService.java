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
			Log.i("VPNService IP", getLocalIpAddress());

			mInterface = builder.setSession("MyVPNService")
					//.setMtu(1500)
					.addAddress("10.0.2.0",32)     	
					.addRoute("0.0.0.0",0).establish();
			//.addDnsServer("8.8.8.8").establish();
			//b. Packets to be sent are queued in this input stream.
			FileInputStream in = new FileInputStream(
					mInterface.getFileDescriptor());
			//b. Packets received need to be written to this output stream.
			FileOutputStream out = new FileOutputStream(
					mInterface.getFileDescriptor());
			//c. The UDP channel can be used to pass/get ip package to/from server
			//DatagramChannel tunnel = DatagramChannel.open();
			// Connect to the server, localhost is used for demonstration only.
			//tunnel.connect(new InetSocketAddress("127.0.0.1",mServerPort));
			//d. Protect this socket, so package send by it will not be feedback to the vpn service.
			//protect(tunnel.socket());

			ByteBuffer packet = ByteBuffer.allocate(32767);
			int timer = 0;
			int length = 0;
			boolean idle;
			
			//e. Use a loop to pass packets.
			while (true) {

				//idle = true;

				// Read the outgoing packet from the input stream.
				length = in.read(packet.array());      
				if (length > 0) {
					//Log.i("VpnService", "Out Pck size " + length);
					Packet pktInfo = new Packet(packet);
					//Log.i("VpnService",  pktInfo.ip4Header.destinationAddress + "");
					
					if(pktInfo.isTCP()){
						//TCPProxy
						//Log.i("VpnService", "TCP: " + pktInfo.tcpHeader.destinationPort + "");
					}else if(pktInfo.isUDP()){
						//UDPProxy
						Log.i("VpnService",  "UDP Destport:" + pktInfo.udpHeader.destinationPort + "");
						UDPtoProxy(packet);
					}
					//TODO Send the outgoing packet to the ProxyServer.
					packet.clear();

					//idle = false;
				}
				Thread.sleep(100);
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

	private void UDPtoProxy(ByteBuffer packet) throws IOException {
		//TODO FARE un THREAD??
		final DatagramSocket clientSocket = new DatagramSocket();
		InetAddress IPAddress = InetAddress.getByName("localhost");
		//send Pkt to Proxy
		DatagramPacket sendPacket = new DatagramPacket(packet.array(), packet.array().length, IPAddress, mServerPort);
		clientSocket.send(sendPacket); 
	}

	private void configure(String parameters) throws Exception {
		// If the old interface has exactly the same parameters, use it!
		if (mInterface != null && parameters.equals(mParameters)) {
			Log.i(TAG, "Using the previous interface");
			return;
		}

		// Configure a builder while parsing the parameters.
		Builder builder = new Builder();
		for (String parameter : parameters.split(" ")) {
			String[] fields = parameter.split(",");
			try {
				switch (fields[0].charAt(0)) {
				case 'm':
					builder.setMtu(Short.parseShort(fields[1]));
					break;
				case 'a':
					builder.addAddress(fields[1], Integer.parseInt(fields[2]));
					break;
				case 'r':
					builder.addRoute(fields[1], Integer.parseInt(fields[2]));
					break;
				case 'd':
					builder.addDnsServer(fields[1]);
					break;
				case 's':
					builder.addSearchDomain(fields[1]);
					break;
				}
			} catch (Exception e) {
				throw new IllegalArgumentException("Bad parameter: " + parameter);
			}
		}

		// Close the old interface since the parameters have been changed.
		try {
			mInterface.close();
		} catch (Exception e) {
			// ignore
		}

		// Create a new interface using the builder and save the parameters.
		mInterface = builder.setSession(mServerAddress)
				.setConfigureIntent(mConfigureIntent)
				.establish();
		mParameters = parameters;
		Log.i(TAG, "New interface: " + parameters);
	}


	public String getLocalIpAddress() {
		String ipv4;
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					// System.out.println("ip1--:" + inetAddress);
					// System.out.println("ip2--:" + inetAddress.getHostAddress());

					// for getting IPV4 format
					if ( !inetAddress.isLoopbackAddress() && InetAddressUtils.isIPv4Address(ipv4 = inetAddress.getHostAddress())) {

						String ip = inetAddress.getHostAddress().toString();
						//  System.out.println("ip---::" + ip);
						// return inetAddress.getHostAddress().toString();
						return ipv4;
					}
				}
			}
		}
		catch (Exception ex) {
			Log.e("IP Address", ex.toString());
		}
		return null;
	}

}
