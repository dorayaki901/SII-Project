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
	FileOutputStream out;
	VpnService vpn;
	Packet pktInfo = null;
	int lengthRequest;
	ByteBuffer packetBuffer;
    int i;
    
    public boolean connected = true;
    
	public ThreadLog(FileOutputStream out, ByteBuffer packet,int length, VpnService vpn,int i) {
		this.packetBuffer = ByteBuffer.allocate(length);
		this.i = i;
		this.out=out;
		this.vpn=vpn;
		try {
			packetBuffer.put(packet.array(), 0, length);
			packetBuffer.position(0);
			pktInfo = new Packet(packetBuffer);
		} catch (UnknownHostException e) {e.printStackTrace();}
	}

	@Override
	public void run() {
		
//				if(pktInfo.ip4Header.destinationAddress.getHostAddress().equals("207.244.90.212")){
//					Log.i("Log1", pktInfo.ip4Header.destinationAddress.getHostAddress()+":"+pktInfo.tcpHeader.destinationPort);
//					TCPSocket(pktInfo);
//				}

		//if(pktInfo.isUDP()){
				//	UDPSocket();
				//	return;
				//}
		//Se è TCP controllare se esiste già un thread che la sta gestendo
		if(pktInfo.isTCP()){
			TCPSocket(pktInfo);
			return;
		}

	}
	
	/**
	 * brief Send and receive messages through an UDP connection
	 */
	private void UDPSocket() {
		DatagramChannel channel = null;
		DatagramSocket ssUDP = null;
		byte[] receiveData=new byte[1024];

		// Send UDP message from APP to the OUTSIDE
		try {
			// Get the payload
			byte[] payload = new byte[lengthRequest-pktInfo.ip4Header.headerLength-Packet.UDP_HEADER_SIZE];
			//			Log.i("ThreadLog", "UDP:"+ lengthRequest+"\nSIZE:"+pktInfo.ip4Header.headerLength);
			pktInfo.backingBuffer.position(pktInfo.ip4Header.headerLength+Packet.UDP_HEADER_SIZE);
			pktInfo.backingBuffer.get(payload, 0, lengthRequest-pktInfo.ip4Header.headerLength-Packet.UDP_HEADER_SIZE);

			//Log.i("ThreadLog", "UDP:"+ new String(payload)+"\nSIZE:"+payload.length);

			channel = DatagramChannel.open();
			ssUDP = channel.socket();

			if(!vpn.protect(ssUDP)){
				Log.i("ThreadLog", "Error protect VPN");
			}

			ssUDP.setReuseAddress(true);
			ssUDP.setBroadcast(true);
			ssUDP.bind(null);

			DatagramPacket sendPacket = new DatagramPacket(payload,payload.length, pktInfo.ip4Header.destinationAddress,pktInfo.udpHeader.destinationPort);

			ssUDP.send(sendPacket);

		} catch (Exception e) {e.printStackTrace();}

		// Receive the responses from OUTSIDE and send it to APP
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

		try {
			ssUDP.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace(); 
			Log.i("ThreadLog", "ERROR");
		}

		Log.i("ThreadLog", "responce:" + (new String(receivePacket.getData())).substring(0, receivePacket.getLength()));

		//		ByteBuffer appSendByte=ByteBuffer.allocate(receivePacket.getLength());
		//		appSendByte.put(receivePacket.getData(), 0, receivePacket.getLength());

		//		Log.i("ThreadLog", "responce:" +appSendByte.array().length+"  /   "+receivePacket.getLength());

		sendToApp(receivePacket.getData(),receivePacket.getLength(),true);	
	}


	/**
	 * brief Send and receive messages through an TCP connection
	 * @param pktInfo
	 */
	private synchronized void TCPSocket(Packet pktInfo) {
		Socket ssTCP = null;
		String responce = null;
		DataOutputStream outToServer = null;
		BufferedReader inFromServer = null;
		Packet pktReply = null;
		try {
			//Extract payload
			byte[] payload = new byte[pktInfo.backingBuffer.remaining()];
			pktInfo.backingBuffer.get(payload, 0, pktInfo.backingBuffer.remaining());
			Log.i("ThreadLog", i +"paket" + pktInfo.toString());
			/// checkout the type of pkt: SYN-SYN/ACK-ACK-FIN 
			//SYN pkt
			if (pktInfo.tcpHeader.isSYN() && !pktInfo.tcpHeader.isACK()){
				Log.d("ThreadLog",i + "SYN pkt received");
				pktReply = SYN_ACKresponse(pktInfo, payload);
				// Send SYN-ACK responce
				out.write(pktReply.backingBuffer.array(), 0, pktReply.backingBuffer.array().length);
				return;
			}
			//SYN-ACK pkt
			//else if (pktInfo.tcpHeader.isSYN() && pktInfo.tcpHeader.isACK()){
			//	pktReply = ACKresponse(pktInfo);
			//}
			//ACK pkt
			if (pktInfo.tcpHeader.isACK() && !pktInfo.tcpHeader.isSYN()){
				Log.i("ThreadLog",i + "ACK pkt received" + new String(payload));
				//CONNECTED = true;
				//pktReply = SYN_ACKresponse(pktInfo);
				//	connected = true;
				//La connessione è stabilita non devo far niente! (forse :'( )
			}
			//Open a connection with the outside
			ssTCP = SocketChannel.open().socket();
			//protect the VPN
			if(!vpn.protect(ssTCP)){
				Log.i("ThreadLog", "Error protect VPN");
			}
			ssTCP.connect(new InetSocketAddress(pktInfo.ip4Header.destinationAddress, pktInfo.tcpHeader.destinationPort));
			//ssTCP = new Socket(pktInfo.ip4Header.destinationAddress, pktInfo.tcpHeader.destinationPort);
			ssTCP.setReuseAddress(true);
			outToServer = new DataOutputStream(ssTCP.getOutputStream());
			inFromServer = new BufferedReader(new InputStreamReader(ssTCP.getInputStream()));

			//send request
		
			//Se ricevo msg di SYN-ACK non devo far nulla!
			if(!(new String(payload)).equals("") && payload!= null){
				Log.d("ThreadLog",i +"Payload sent to server" +  new String(payload));
				outToServer.write(payload,0,payload.length);
				
				//receive the response
				responce = inFromServer.readLine();
				Log.d("ThreadLog", i + "-Responce:  " + responce);
			}
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
	 * @param payload 
	 * @return
	 */
	private Packet SYN_ACKresponse(Packet syn_pkt, byte[] payload) {
		int payloadSize = syn_pkt.backingBuffer.limit() - syn_pkt.backingBuffer.position();
		Log.i("ThreadLog1",i+" " + syn_pkt.toString());
		byte flags = syn_pkt.tcpHeader.flags;

		flags = setBit(1, 2, flags); // Ack bit
		flags = setBit(1, 5, flags); // Syn bit

		long sequenceNum = (int) Math.random(); // Random number 
		long ackNum = syn_pkt.tcpHeader.sequenceNumber + 1; // increment the seq. num.

		//Log.i("ThreadLog", syn_pkt.tcpHeader.flags + ": "+ Integer.toBinaryString(Integer.valueOf(flags)));
		ByteBuffer buffer = syn_pkt.backingBuffer;
		buffer.position(0);
		syn_pkt.updateTCPBuffer(buffer, flags, sequenceNum, ackNum, payloadSize);
		syn_pkt.updateSourceAndDestination();
		Log.i("ThreadLog2",i+" "+ syn_pkt.toString());

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

	private void sendToApp(byte[] receiveData,int length, boolean protocol)  {
		//UDPheader+ipheader+length
		pktInfo.updateSourceAndDestination();
		if (protocol){
			pktInfo.updateUDPBuffer(receiveData,length);

		}//else
		//pktInfo.updateTCPBuffer(ByteBuffer.wrap(receiveData2), , 0, 0, 0);
		Packet prova = null;
		try {
			prova = new Packet(pktInfo.backingBuffer);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		Log.i("ThreadLog-", "dim. responce:" +pktInfo.ip4Header.totalLength+"\n"+prova.toString());
		Log.i("ThreadLog-", "body:"+(new String(pktInfo.backingBuffer.array())));
		try {

			out.write(pktInfo.backingBuffer.array(), 0, prova.ip4Header.totalLength);

		} catch (IOException e) {e.printStackTrace();}

	}


}
