package com.si.android.vpnproxy;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import android.net.VpnService;
import android.util.Log;

public class TCPManager {

	private static int MAX_MTU = 5000;
	private static String TAG = "TCPManager";
	public Packet pktInfo;
	private ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue;
	private VpnService vpn;
	private DataOutputStream outToServer;
	private Socket ssTCP = null;
	private InputStream inFromServer = null;


	public TCPManager(Packet pktInfo, ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue, VpnService vpn) {
		this.pktInfo = pktInfo;
		this.sentoToAppQueue = sentoToAppQueue;
		this.vpn = vpn;

	}

	/**
	 * Main Class for manage the connection
	 * @param payload The TCP packet payload
	 * @return false in case of errors. the Manager Thread will be terminated -->RivedereS
	 */
	public boolean managePKT(byte[] payload) {

		/** 1. check the type of pkt: SYN-SYN/ACK-FIN **/ 
		//SYN pkt
		if (pktInfo.tcpHeader.isSYN() && !pktInfo.tcpHeader.isACK()){
			manageSYN();
			return true;
		}

		//FIN pkt
		if (pktInfo.tcpHeader.isFIN()){
			manageFIN();
			if (ssTCP!=null)
				try {
					ssTCP.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			return false;
		}

		/** 2 If the pkt has payload manage the request NB:(Pure ACK Ignored) **/ 
		byte[] response = new byte[ToyVpnService.MAX_PACKET_LENGTH];
		int responseLen = 0;

		//if the msg have some payload, resent it to the outside Server
		if( payload!= null && !(new String(payload)).isEmpty() ){

			//Send First Ack
			//manageACK();

			try {
				if(ssTCP==null){ // only the fist time. If I have already a connection, send directly the payload
					ssTCP = openConnection();
					this.outToServer = new DataOutputStream(ssTCP.getOutputStream());
					this.inFromServer  = ssTCP.getInputStream();
				}
				//Send request to the outside-server
				Log.i("ThreadLog",pktInfo.tcpHeader.sourcePort+" - "+"Payload sent to server " +  (new String(payload)));
				outToServer.write(payload,0,payload.length);

				// Reading outside-server response
				Log.i(TAG, "Reading outside-server response");
				responseLen = 0;
				int count = 0;
				while ((count = inFromServer.read(response,responseLen,ToyVpnService.MAX_PACKET_LENGTH-responseLen)) > 0) {
					responseLen+=count;
				}

			} catch (IOException e1) {
				e1.printStackTrace();
			}	

			if(responseLen <0){//The server didn't sent anythings. The connection will be closed 
				//TODO Send FIN? continuo??
				return false;
			}
			sendToApp(response, responseLen);
			Log.i("ThreadLog",pktInfo.tcpHeader.sourcePort+" - "+ responseLen + " -Response:  " +" \n" + new String(response ) );



		}
		return true;
	}

	/**
	 * Send an empty ack pkt
	 */
	private void manageACK() {
		// Send SYN-ACK response
		pktInfo.swapSourceAndDestination();
		int payloadReceiveLen = (pktInfo.backingBuffer.capacity()-pktInfo.ip4Header.headerLength-pktInfo.tcpHeader.headerLength);
		long acknowledgmentNumber = pktInfo.tcpHeader.sequenceNumber + payloadReceiveLen;// +length;
		long sequenceNum = pktInfo.tcpHeader.acknowledgementNumber;
		pktInfo.updateTCPBuffer(pktInfo.backingBuffer, (byte) (Packet.TCPHeader.ACK), sequenceNum, acknowledgmentNumber, 0);

		ByteBuffer bufferFromNetwork = pktInfo.backingBuffer;
		bufferFromNetwork.flip();

		sentoToAppQueue.add(bufferFromNetwork);

		pktInfo.updateSourceAndDestination();

	}

	/**
	 * Send the SYN/ACK response
	 */
	public void manageSYN() {
		// Send SYN-ACK response
		pktInfo.swapSourceAndDestination();
		long sequenceNum = 1 ;//(int) Math.random(); // Random number 
		long ackNum = pktInfo.tcpHeader.sequenceNumber + 1; // increment the seq. num.
		pktInfo.updateTCPBuffer(pktInfo.backingBuffer, (byte) (Packet.TCPHeader.SYN | Packet.TCPHeader.ACK), sequenceNum, ackNum, 0);

		ByteBuffer bufferFromNetwork = pktInfo.backingBuffer;
		bufferFromNetwork.flip();

		sentoToAppQueue.add(bufferFromNetwork);
	}

	/**
	 * Send the FIN-response
	 */
	public void manageFIN() {
		// Send SYN-ACK response
		Log.i(TAG, "FIN request");
		pktInfo.swapSourceAndDestination();
		long sequenceNum = pktInfo.tcpHeader.acknowledgementNumber ;//(int) Math.random(); // Random number 
		long ackNum = pktInfo.tcpHeader.sequenceNumber + 1; // increment the seq. num.
		pktInfo.updateTCPBuffer(pktInfo.backingBuffer,(byte) (Packet.TCPHeader.FIN | Packet.TCPHeader.ACK), sequenceNum, ackNum, 0);

		ByteBuffer bufferFromNetwork = pktInfo.backingBuffer;
		bufferFromNetwork.flip();

		sentoToAppQueue.add(bufferFromNetwork);
	}

	/**
	 * Open a new connection with the outsideServer
	 * @return The socket descriptor for the connection
	 */
	public Socket openConnection() {
		Socket ssTCP = null;
		Log.i(TAG,pktInfo.tcpHeader.sourcePort + " - " + " New connection: " + pktInfo.ip4Header.destinationAddress + ":"+ pktInfo.tcpHeader.destinationPort);

		try {			
			ssTCP = SocketChannel.open().socket();

			//protect the VPN
			if(!vpn.protect(ssTCP)){
				Log.i("ThreadLog", "Error protect VPN");
			}
			ssTCP.connect(new InetSocketAddress(pktInfo.ip4Header.destinationAddress, pktInfo.tcpHeader.destinationPort));
			ssTCP.setReuseAddress(true);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return ssTCP;
	}

	/**
	 * 
	 * @param receiveData 
	 * @param length
	 * @param protocol
	 */
	private void sendToApp(byte[] payloadResponse, int payloadLengthTOT) {

		int payloadLength = payloadLengthTOT;
		int payloadSent = 0;
		int payloadReceiveLen = (pktInfo.backingBuffer.capacity()-pktInfo.ip4Header.headerLength-pktInfo.tcpHeader.headerLength);

		long acknowledgmentNumber = pktInfo.tcpHeader.sequenceNumber + payloadReceiveLen;
		long sequenceNumPrec = pktInfo.tcpHeader.acknowledgementNumber;

		long sequenceNum = 0;
		long payloadLenghtPrec = 0;
		
		while(payloadLength>0){
			if(payloadLength>MAX_MTU)
				payloadSent = MAX_MTU;
			else 
				payloadSent = payloadLength;
			
			int lengthPacket = payloadSent + pktInfo.ip4Header.headerLength;
			lengthPacket += pktInfo.tcpHeader.headerLength;		

			try {
				//Log.d("sendToApp", "OLD1:" + pktInfo.toString() + "\n" + new String(pktInfo.backingBuffer.array()));
				//Creating the new response packet, directly from the request pkt
				pktInfo.backingBuffer.position(0);

				ByteBuffer newBuffer = ByteBuffer.allocateDirect(lengthPacket);
				Packet sendToAppPkt = new Packet(pktInfo.backingBuffer);
				pktInfo.backingBuffer.position(0);
				sendToAppPkt.backingBuffer = null;

				//Calculating the new ack/sqn numbers
				sequenceNum = sequenceNumPrec  + payloadLenghtPrec;

				//Update the new packet with the right fields
				sendToAppPkt.swapSourceAndDestination();
								
				sendToAppPkt.updateTCPBuffer(newBuffer, (byte) ( Packet.TCPHeader.PSH |Packet.TCPHeader.ACK), sequenceNum, 
						acknowledgmentNumber , payloadSent , (payloadResponse), payloadLengthTOT-payloadLength);
				sendToAppPkt.backingBuffer.position(0);

				//Send the new packet through the out channel
				ByteBuffer bufferFromNetwork = sendToAppPkt.backingBuffer;
				synchronized(this){
					sentoToAppQueue.add(bufferFromNetwork);
				}
				
				sequenceNumPrec = sequenceNum;
				payloadLenghtPrec = payloadSent;
				payloadLength-= payloadSent;
				Log.d("sendToApp1", "SENT TO APP:" + new String(bufferFromNetwork.array()));

			} catch (Exception e) {e.printStackTrace();}

		}

	}

}
