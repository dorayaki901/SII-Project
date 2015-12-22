package com.example.android.toyvpn;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import junit.framework.Protectable;
import android.net.VpnService;
import android.util.Log;

public class ThreadLog implements Runnable {
	ByteBuffer packetBuffer;
	FileOutputStream out;
	VpnService vpn;
	Packet pktInfo = null;
	
	public ThreadLog(FileOutputStream out, ByteBuffer packet, VpnService vpn) {
		this.packetBuffer=packet;
		this.out=out;
		this.vpn=vpn;
		try {
			pktInfo = new Packet(packetBuffer);
		} catch (UnknownHostException e) {
			Log.i("ciao", "hola!");
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		
		if(pktInfo.ip4Header.destinationAddress.getHostAddress().equals("151.24.140.91")){
			Log.i("Log1", pktInfo.ip4Header.destinationAddress.getHostAddress()+":"+pktInfo.tcpHeader.destinationPort);
			TCPSocket(pktInfo);
		}
			
//		if(pktInfo.isTCP() && pktInfo.ip4Header.destinationAddress.getHostAddress().equals("192.168.1.2")){
//			Log.i("Log1", "eureca");
//			TCPSocket(pktInfo);
//			return;
//		}
		
//		if(pktInfo.isUDP()){
//			UDPSocket(pktInfo);
//			return;
//		}
		
	}
	
	private void UDPSocket(Packet pktInfo) {
			DatagramSocket ssUDP = null;
			byte[] receiveData = new byte[3000];
			try {
				ssUDP = new DatagramSocket(new InetSocketAddress( 
						pktInfo.ip4Header.destinationAddress, pktInfo.udpHeader.destinationPort));
				 vpn.protect(ssUDP);
				 ssUDP.setReuseAddress(true);
				 ssUDP.setBroadcast(true);
				
			} catch (SocketException e) {e.printStackTrace();}
			
			 try {
				ssUDP.send(new DatagramPacket(pktInfo.backingBuffer.array(),pktInfo.backingBuffer.array().length));
			} catch (IOException e) {e.printStackTrace();}
			 
			 DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			 
		      try {
				ssUDP.receive(receivePacket);
			} catch (IOException e) {e.printStackTrace();}
		      
		     //sendToApp(receivePacket.getData());	
		
	}

	private synchronized void TCPSocket(Packet pktInfo) {
		Socket ssTCP = null;
		String responce = null;
		DataOutputStream outToServer = null;
		BufferedReader inFromServer = null;
		
		try {
			ssTCP = new Socket();
			Log.i("Log1", "EUREKA!");
			vpn.protect(ssTCP);
			Log.i("Log1", "EUREKA1!");
			ssTCP.connect(new InetSocketAddress(pktInfo.ip4Header.destinationAddress, pktInfo.tcpHeader.destinationPort));
			Log.i("Log1", "EUREKA2!");
			//ssTCP = new Socket(pktInfo.ip4Header.destinationAddress, pktInfo.tcpHeader.destinationPort);
			
			
			ssTCP.setReuseAddress(true);
		} catch (Exception e) {e.printStackTrace();}
		
		
		try {
			outToServer = new DataOutputStream(ssTCP.getOutputStream());
			inFromServer = new BufferedReader(new InputStreamReader(ssTCP.getInputStream()));
		} catch (IOException e1) {e1.printStackTrace();}
		
		
		try {
			outToServer.write(pktInfo.backingBuffer.array(),0,pktInfo.backingBuffer.array().length);
			responce=inFromServer.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		 
		Log.i("Log1", responce);
		
		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//sendToApp(responce.getBytes());	
		
	}
	
	private void sendToApp(byte[] receiveData2)  {
		try {
			out.write(receiveData2, 0, receiveData2.length);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	

}
