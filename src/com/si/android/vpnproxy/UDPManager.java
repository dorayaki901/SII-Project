package com.si.android.vpnproxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.net.VpnService;
import android.util.Log;

public class UDPManager {
	private static String TAG = "UDPManager";
	public Packet pktInfo;
	private ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue;
	private VpnService vpn;


	public UDPManager(Packet pktInfo, ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue, VpnService vpn) {
		this.pktInfo = pktInfo;
		this.sentoToAppQueue = sentoToAppQueue;
		this.vpn = vpn;

	}

	/**
	 * Main Class for manage the connection
	 * @param payload The TCP packet payload
	 * @return false in case of errors. the Manager Thread will be terminated -->RivedereS
	 * @throws IOException 
	 */
	public boolean managePKT(byte[] payload) throws IOException {
		DatagramSocket ssUDP = null;
		byte[] receiveData=new byte[ToyVpnService.MAX_PACKET_LENGTH];
		
		//Open the new UDP connection
		ssUDP = openConnection();
		if(ssUDP == null)
			return false;
		DatagramPacket sendPacket = new DatagramPacket(payload,payload.length, pktInfo.ip4Header.destinationAddress,pktInfo.udpHeader.destinationPort);
		
		// Send UDP message from APP to the OUTSIDE
		ssUDP.send(sendPacket);
		
		// Receive the responses from OUTSIDE 
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		
		try {
			ssUDP.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace(); 
			Log.i(TAG, "ERROR");
		}
		
		//Send the response to the APP
		sendToApp(receivePacket.getData(),receivePacket.getLength());	
	
		return true;
	}
	
	/**
	 * Open a new connection with the outsideServer
	 * @return The socket descriptor for the connection
	 * @throws IOException 
	 */
	public DatagramSocket openConnection() throws IOException {
		DatagramChannel channel = null;
		DatagramSocket ssUDP = null;
		
		if (channel==null){
			channel = DatagramChannel.open();
			ssUDP = channel.socket();

			if(!vpn.protect(ssUDP)){
				Log.i(TAG, "Error protect VPN");
			}

			ssUDP.setReuseAddress(true);
			ssUDP.setBroadcast(true);
			ssUDP.bind(null);
		}
		return ssUDP;
	}

	/**
	 * 
	 * @param receiveData 
	 * @param length
	 * @param protocol
	 */
	private void sendToApp(byte[] payloadResponce, int payloadLength) {
		
		int lengthPacket = payloadLength + pktInfo.ip4Header.headerLength;

		lengthPacket += Packet.UDP_HEADER_SIZE;		

		synchronized(this){
			try {
				
				pktInfo.backingBuffer.position(0);

				Packet sendToAppPkt = new Packet(pktInfo.backingBuffer,lengthPacket);
				pktInfo.backingBuffer.position(0);
				ByteBuffer newBuffer = ByteBuffer.allocateDirect(lengthPacket);

				if (pktInfo.backingBuffer.array().length>lengthPacket)
					newBuffer.put(pktInfo.backingBuffer.array(),0, lengthPacket);
				else
					newBuffer.put(pktInfo.backingBuffer.array());// QUI OK
				
				sendToAppPkt.backingBuffer = newBuffer;	
				sendToAppPkt.updateSourceAndDestination();
				sendToAppPkt.updateUDPBuffer(payloadResponce, payloadLength);
				
				sendToAppPkt.backingBuffer.position(0);
				ByteBuffer bufferFromNetwork = sendToAppPkt.backingBuffer;

				sentoToAppQueue.add(bufferFromNetwork);

			} catch (Exception e) {e.printStackTrace();}
		}


	}

}
