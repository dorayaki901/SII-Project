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
import java.util.Random;

import junit.framework.Protectable;
import android.hardware.Camera.Parameters;
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
	
	private boolean connected = true;
	
	private synchronized void TCPSocket(Packet pktInfo) {
		Socket ssTCP = null;
		String responce = null;
		DataOutputStream outToServer = null;
		BufferedReader inFromServer = null;
		Packet pktReply = null;
		try {
			// checkout the type of pkt: SYN-SYN/ACK-ACK-FIN
			//SYN pkt
			if (pktInfo.tcpHeader.isSYN() && !pktInfo.tcpHeader.isACK()){
				pktReply = SYN_ACKresponse(pktInfo);
			}
			//SYN-ACK pkt
			else if (pktInfo.tcpHeader.isSYN() && pktInfo.tcpHeader.isACK()){
				pktReply = ACKresponse(pktInfo);
			}
			//ACK pkt
			else if (pktInfo.tcpHeader.isACK() && !pktInfo.tcpHeader.isSYN()){
				//pktReply = SYN_ACKresponse(pktInfo);
				connected = true;
				//La connessione è stabilita non devo far niente! (forse :'( )
				// E così sia per il msg di ack vero e proprio, che per gli altri messaggi con playload giusto (forse sempre)
			}
			
			//Open a connection with the outside
			ssTCP = SocketChannel.open().socket();
			//protect the VPN
			vpn.protect(ssTCP);
			ssTCP.connect(new InetSocketAddress(pktInfo.ip4Header.destinationAddress, pktInfo.tcpHeader.destinationPort));
			//ssTCP = new Socket(pktInfo.ip4Header.destinationAddress, pktInfo.tcpHeader.destinationPort);
			ssTCP.setReuseAddress(true);

			outToServer = new DataOutputStream(ssTCP.getOutputStream());
			inFromServer = new BufferedReader(new InputStreamReader(ssTCP.getInputStream()));
			
			//send request
			if(connected){
				outToServer.write(pktInfo.backingBuffer.array(),0,pktInfo.backingBuffer.array().length);
			}
			//receive the response
			responce = inFromServer.readLine();

			Log.i("ThreadLog-Responce Status", responce);

			Thread.sleep(200);
		} catch (Exception e) {
			e.printStackTrace();
		}

		//sendToApp(responce.getBytes());	

	}

	private Packet ACKresponse(Packet pktInfo2) {
		// TODO Auto-generated method stub
		return null;
	}
	
	/**
	 * brief  Built a new Syn_Ack packet responding to the original syn_pkt 
	 * @param pkt
	 * @return
	 */
	private Packet SYN_ACKresponse(Packet syn_pkt) {
		int payloadSize = syn_pkt.backingBuffer.limit() - syn_pkt.backingBuffer.position();
		byte[] payload = new byte[pktInfo.backingBuffer.remaining()];
		pktInfo.backingBuffer.get(payload, 0, pktInfo.backingBuffer.remaining());
		
		byte flags = syn_pkt.tcpHeader.flags;
		
		flags = setBit(1, 4, flags); // Ack bit
		flags = setBit(1, 7, flags); // Syn bit
		
		long sequenceNum = (int) Math.random(); // Random number 
		long ackNum = syn_pkt.tcpHeader.sequenceNumber + 1; // increment the seq. num.
		
		//TODO ci va il payload??? bho
		ByteBuffer buffer = ByteBuffer.wrap(payload);
		syn_pkt.updateTCPBuffer(buffer, flags, sequenceNum, ackNum, payloadSize);
		
		return syn_pkt;
	}

	/**
	 * Set the bit in position to the valueOfBit value
	 * @param valueOfBit
	 * @param pos
	 * @param byteToSet
	 * @return the new byte
	 */
	private byte setBit(int valueOfBit,int pos, byte byteToSet){
		if(valueOfBit==1)
			return( (byte) (byteToSet | (1 << (pos-1))) );
		else
			return( (byte) (byteToSet & ~(1 << pos+1)) );
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
