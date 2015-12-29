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
import java.nio.CharBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

import junit.framework.Protectable;
import android.net.VpnService;
import android.util.Log;

public class ThreadLog implements Runnable {
	ByteBuffer packetBuffer;
	FileOutputStream out;
	VpnService vpn;
	Packet pktInfo = null;
	
	public ThreadLog(FileOutputStream out, ByteBuffer packet,int length, VpnService vpn) {
		this.packetBuffer = ByteBuffer.allocate(length);
				
		this.out=out;
		this.vpn=vpn;
		try {
			packetBuffer.put(packet.array(), 0, length);
			packetBuffer.position(0);
			Log.i("cdsf", ""+length + packetBuffer.limit() + packetBuffer.capacity());
			pktInfo = new Packet(packetBuffer);
		} catch (UnknownHostException e) {e.printStackTrace();}
	}
	
	@Override
	public void run() {
		
//		if(pktInfo.ip4Header.destinationAddress.getHostAddress().equals("151.15.155.150")){
//			Log.i("Log1", pktInfo.ip4Header.destinationAddress.getHostAddress()+":"+pktInfo.tcpHeader.destinationPort);
//			TCPSocket(pktInfo);
//		}
			
//		if(pktInfo.isTCP()){
//			TCPSocket(pktInfo);
//			return;
//		}
		
		if(pktInfo.isUDP()){
			UDPSocket(pktInfo);
			return;
		}
		
	}
	
	private void UDPSocket(Packet pktInfo) {
		DatagramChannel channel = null;
		DatagramSocket ssUDP = null;
		byte[] receiveData = new byte[512];
		try {
			byte[] payload = new byte[pktInfo.backingBuffer.remaining()];
			pktInfo.backingBuffer.get(payload, 0, pktInfo.backingBuffer.remaining());
			Log.i("ThreadLog", "UDP:2 " + "\nCONTENT2: "+ new String(payload));
			//Log.i("ThreadLog", "UDP:" + pktInfo.toString() + "\nCONTENT: "+(new String(pktInfo.backingBuffer.array())) + " " + pktInfo.backingBuffer.toString() + " " + pktInfo.backingBuffer.capacity());

			channel = DatagramChannel.open();
			ssUDP = channel.socket();
			
			if(!vpn.protect(ssUDP)){
				Log.i("ThreadLog", "Error protect VPN");
			}
			
			ssUDP.setReuseAddress(true);
			ssUDP.setBroadcast(true);
			ssUDP.bind(null);
			
			//TODO
			byte[] appByte=new byte[46];
			pktInfo.backingBuffer.get(appByte, 0, 46);
			
			DatagramPacket sendPacket = new DatagramPacket(appByte,appByte.length, pktInfo.ip4Header.destinationAddress,pktInfo.udpHeader.destinationPort);
					
			ssUDP.send(sendPacket);
			
		} catch (Exception e) {e.printStackTrace();}
		
		 
		 DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		 
	      try {
//	    	  Log.i("ThreadLog", "RECEIVING");
		      
			ssUDP.receive(receivePacket);
		} catch (IOException e) {e.printStackTrace(); 
			Log.i("ThreadLog", "ERROR");
		 }
	      
	      Log.i("ThreadLog", "responce:" + (new String(receivePacket.getData())));
	      
	     sendToApp(receivePacket.getData(),true);	
	
}

	private synchronized void TCPSocket(Packet pktInfo) {
		Socket ssTCP = null;
		String responce = null;
		DataOutputStream outToServer = null;
		BufferedReader inFromServer = null;
		
		try {
			ssTCP = SocketChannel.open().socket();
			vpn.protect(ssTCP);
			ssTCP.connect(new InetSocketAddress(pktInfo.ip4Header.destinationAddress, pktInfo.tcpHeader.destinationPort));
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
			e.printStackTrace();
		}
		 
		Log.i("ThreadLog-Responce Status", responce);
		
		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
		
			e.printStackTrace();
		}
		//sendToApp(responce.getBytes());	
		
	}
	
	private void sendToApp(byte[] receiveData2, boolean protocol)  {
		pktInfo.swapSourceAndDestination();
		
		if (protocol){
			pktInfo.updateUDPBuffer(ByteBuffer.wrap(receiveData2),receiveData2.length);
			Log.i("ThreadLog-SWAP","SWAP: "+pktInfo.toString());	
		}
			
//		else
//			pktInfo.updateTCPBuffer(ByteBuffer.wrap(receiveData2), 0 , sequenceNum, ackNum, payloadSize);
		try {
			out.write(receiveData2, 0, receiveData2.length);
			
		} catch (IOException e) {
		
			e.printStackTrace();
		}
		
	}
	

}
