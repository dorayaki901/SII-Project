package com.si.android.vpnproxy;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedReader;
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
	private static String TAG = "ThreadLog";
	public boolean connected = false;
	private PipedInputStream readPipe;

	public ThreadLog(FileOutputStream out, ByteBuffer packet,int length, VpnService vpn,int i) {
		inizializationPacket(out, packet, length, vpn, i);
	}

	private void inizializationPacket(FileOutputStream out, ByteBuffer packet,int length, VpnService vpn,int i) {
		this.packetBuffer = ByteBuffer.allocate(length);
		this.i = i;
		this.lengthRequest=length;

		if (pktInfo==null){
			this.out=out;
			this.vpn=vpn;
		}

		try {
			packetBuffer.put(packet.array(), 0, length);
			packetBuffer.position(0);
			pktInfo = new Packet(packetBuffer);
		} catch (UnknownHostException e) {e.printStackTrace();}

	}

	@Override
	public void run() {

		if(pktInfo.ip4Header.destinationAddress.getHostAddress().equals("207.244.90.212")){
			Log.i(TAG, "Starting new Thread for connection at:" + pktInfo.ip4Header.destinationAddress.getHostAddress() + ":" + pktInfo.tcpHeader.destinationPort);
			TCPSocket(pktInfo);
		}

		//		if(pktInfo.isUDP()){
		//			UDPSocket();
		//			return;
		//		}
		//		if(pktInfo.isTCP()){
		//			TCPSocket(pktInfo);
		//			return;
		//		}

	}

	/**
	 * brief Send and receive messages through an UDP connection
	 */
	private void UDPSocket() {
		DatagramChannel channel = null;
		DatagramSocket ssUDP = null;
		byte[] receiveData=new byte[1500];
		byte[] receivedPacket = new byte[ToyVpnService.MAX_PACKET_LENGTH];

		while(true){
			// Send UDP message from APP to the OUTSIDE
			try {

				//QUI MI DA NEGATIVE ARRAY SIZE
				byte[] payload = new byte[lengthRequest-pktInfo.ip4Header.headerLength-Packet.UDP_HEADER_SIZE];
				//			Log.i("ThreadLog", "UDP:"+ lengthRequest+"\nSIZE:"+pktInfo.ip4Header.headerLength);
				pktInfo.backingBuffer.position(pktInfo.ip4Header.headerLength+Packet.UDP_HEADER_SIZE);
				pktInfo.backingBuffer.get(payload, 0, lengthRequest-pktInfo.ip4Header.headerLength-Packet.UDP_HEADER_SIZE);

				Log.i("ThreadLog", "UDP:"+ new String(payload)+"\nSIZE:"+payload.length);


				if (channel==null){
					channel = DatagramChannel.open();
					ssUDP = channel.socket();

					if(!vpn.protect(ssUDP)){
						Log.i("ThreadLog", "Error protect VPN");
					}

					ssUDP.setReuseAddress(true);
					ssUDP.setBroadcast(true);
					ssUDP.bind(null);
				}

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

			//Log.i("ThreadLog", "responce:" + (new String(receivePacket.getData())).substring(0, receivePacket.getLength()));		
			//		ByteBuffer appSendByte=ByteBuffer.allocate(receivePacket.getLength());
			//		appSendByte.put(receivePacket.getData(), 0, receivePacket.getLength());		
			//		Log.i("ThreadLog", "responce:" +appSendByte.array().length+"  /   "+receivePacket.getLength());

			sendToApp(receivePacket.getData(),receivePacket.getLength(),true);	

			Log.i("ThreadLog", "post send to app");
			try {
				lengthRequest=readPipe.read(receivedPacket);
				pktInfo = new Packet(ByteBuffer.wrap(receivedPacket));

				Log.i("ThreadLog - Lunghezza", lengthRequest+"");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		}
	}

	private void sendToApp(byte[] receiveData,int length, boolean protocol)  {
		//UDPheader+ipheader+length
		ByteBuffer bufferToSend = null;
		Packet prova = null;
		int lengthPacket=length+pktInfo.ip4Header.headerLength;

		//Instanziazine ByteBuffer di risposta
		lengthPacket += (protocol) ? Packet.UDP_HEADER_SIZE: pktInfo.tcpHeader.headerLength;		
		bufferToSend =  ByteBuffer.allocate(lengthPacket);

		//Log.i("ThreadLog", lengthPacket+" / "+pktInfo.backingBuffer.array().length);

		//Clone dell'header di richiesta


		if (pktInfo.backingBuffer.array().length>lengthPacket)
			bufferToSend.put(pktInfo.backingBuffer.array(),0, lengthPacket);
		else
			bufferToSend.put(pktInfo.backingBuffer.array());

		//Creazine Pachetto di risposta
		try {
			bufferToSend.position(0);
			prova = new Packet(bufferToSend);
		} catch (UnknownHostException e1) {	e1.printStackTrace(); }

		prova.updateSourceAndDestination();

		//Log.i("ThreadLog",prova.toString());

		try{

			if (protocol){
				prova.updateUDPBuffer(receiveData,length);
			}//else
			//pktInfo.updateTCPBuffer(ByteBuffer.wrap(receiveData2), , 0, 0, 0);

		}catch (Exception e1) {	e1.printStackTrace(); }



		//		Log.i("ThreadLog-", "dim. responce:" +prova.ip4Header.totalLength+"\n"+prova.toString());
		//		Log.i("ThreadLog - Post Update", "body:\n"+(new String(prova.backingBuffer.array())));
		try {

			out.write(prova.backingBuffer.array(), 0, prova.ip4Header.totalLength);

		} catch (Exception e) {e.printStackTrace();}

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
		connected = false;
		byte[] receivedPacket = new byte[ToyVpnService.MAX_PACKET_LENGTH];
		try {

			do{
				if(pktInfo == null){
					Log.i(TAG,i + "PKT NULL");
					continue;
				}
				//Extract payload
				byte[] payload = new byte[pktInfo.backingBuffer.remaining()];
				pktInfo.backingBuffer.get(payload, 0, pktInfo.backingBuffer.remaining());

				//Log.i("ThreadLog", i +"packet" + pktInfo.toString());

				/// checkout the type of pkt: SYN-SYN/ACK-ACK-FIN 
				//SYN pkt
				if (pktInfo.tcpHeader.isSYN() && !pktInfo.tcpHeader.isACK()){
					Log.d("ThreadLog",i + "SYN pkt received");
					pktReply = SYN_ACKresponse(pktInfo, payload);
					// Send SYN-ACK response
					out.write(pktReply.backingBuffer.array(), 0, pktReply.backingBuffer.array().length);
				}

				//ACK pkt
				if (pktInfo.tcpHeader.isACK() && !pktInfo.tcpHeader.isSYN()){
					Log.i("ThreadLog",i + "ACK pkt received" + new String(payload));
					//pktReply = SYN_ACKresponse(pktInfo);
				}

				//Se il msg ha payload apro connessione e lo mando al server
				if(!(new String(payload)).equals("") && payload!= null){
					//Log.i(TAG,i + " pkt:  " + new String(payload));

					if(ssTCP==null){ // only the fist time
						Log.i(TAG,i + " NEW CONNECTION TO: " + pktInfo.ip4Header.destinationAddress + ":"+ pktInfo.tcpHeader.destinationPort);

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

					}
					//send request
					Log.d("ThreadLog",i +"Payload sent to server" +  new String(payload));

					outToServer.write(payload,0,payload.length);

					//receive the response
					//char[] buff = new char[6000];
					int n = 0;
					//n = inFromServer.read(buff, 0, buff.length);
					String line = inFromServer.readLine();
					responce = "";
					while(line!=null && !line.equals("")){
						responce += line;
						line = inFromServer.readLine();
					}
					//responce = new String(buff);
					Log.d("ThreadLog", i + "-Responce:  " + n +" \n" + responce );

					//TODO sendToApp(responce.getBytes());	
					//Read from the pipe for the responce

				}

				int length = 0;
				int dimP = 0;
				int tot = 0;
				byte[] sizeBuff = new byte[4];
				while(tot<4){
					length = readPipe.read(sizeBuff,tot,4-tot); // read the length of the pkt
					if (length <0){
						Log.i(TAG, "Error reading from pipe");
						return;
					}
					tot += length ;
				}
				Log.i(TAG, i + "byteRead	1: " + tot );
				tot = 0;
				dimP = ByteBuffer.wrap(sizeBuff).getInt();
				Log.i(TAG, i + "DimP: " + dimP );
				while(dimP>tot){
					length = readPipe.read(receivedPacket,tot,dimP-tot);				
					if (length <0){
						Log.i(TAG, "Error reading from pipe");
						return;
					}
					tot += length ;
				}								
				Log.i(TAG, i + "TOT: " + tot);

				ByteBuffer packetBuffer = ByteBuffer.allocate(tot);
				packetBuffer.put(receivedPacket, 0, tot);
				packetBuffer.flip();
				pktInfo  = new Packet(packetBuffer);
//				pktInfo = new Packet(ByteBuffer.wrap(receivedPacket));

				Log.i(TAG, i + "Read From Pipe: " + tot + " -- " + new String(packetBuffer.array()));
				Log.i(TAG, i + "Read From Pipe:" + pktInfo.toString());

				Thread.sleep(200);
			}while(true);

		} catch (Exception e) {
			e.printStackTrace();
		}



	}

	/**
	 * brief  Built a new Syn_Ack packet responding to the original syn_pkt 
	 * @param pkt
	 * @param payload 
	 * @return
	 */
	private Packet SYN_ACKresponse(Packet syn_pkt, byte[] payload) {
		int payloadSize = syn_pkt.backingBuffer.limit() - syn_pkt.backingBuffer.position();
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

		return syn_pkt;
	}

	/**
	 * Set the bit in pos to the valueOfBit value
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

	/** 
	 * Send to app the incoming packet
	 * @param receiveData 
	 * @param length
	 * @param protocol
	 */


	public void setPipe(PipedInputStream readPipe) {
		this.readPipe=readPipe;		
	}


}
