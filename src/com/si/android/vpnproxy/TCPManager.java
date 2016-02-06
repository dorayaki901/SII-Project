package com.si.android.vpnproxy;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Random;
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
	private Random random = new Random();
	private PipedInputStream readPipe = null;

	public TCPManager(Packet pktInfo, ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue, VpnService vpn, PipedInputStream readPipe) {
		this.pktInfo = pktInfo;
		this.sentoToAppQueue = sentoToAppQueue;
		this.vpn = vpn;
		this.readPipe = readPipe;
	}

	/**
	 * Main Class for manage the connection
	 * @param payload The TCP packet payload
	 * @return false in case of errors. the Manager Thread will be terminated -->RivedereS
	 * @throws InterruptedException 
	 */
	public boolean managePKT(byte[] payload) throws InterruptedException {
		ByteBuffer totalPayload = ByteBuffer.allocateDirect(ToyVpnService.MAX_PACKET_LENGTH);
		totalPayload.put(payload);
		ByteBuffer app;

		/** 1. check the type of pkt: SYN-SYN/ACK-FIN **/
		if (pktInfo.tcpHeader.isRST()){ // Reset from peer
			if (ssTCP!=null)
				try {
					ssTCP.close();
					// TODO verificare se devo fa qualcosa
				} catch (IOException e) {
					e.printStackTrace();
				}
			return false;
		}
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
		if(!(new String(payload)).isEmpty()){
			//manageACK();
			//Send First Ack
			
			/**********************************************************/
			/****Management of fragmentation request ****/
			if(!pktInfo.tcpHeader.isPSH()){
				if( (app = waitForTotalPayload()) == null){
					return false;
				}
				totalPayload.put(app);
			}
			/**********************************************************/
			
			
			try {
				if(ssTCP==null){ // only the fist time. If I have already a connection, send directly the payload
					ssTCP = openConnection();
					this.outToServer = new DataOutputStream(ssTCP.getOutputStream());
					this.inFromServer  = ssTCP.getInputStream();
				}

				//TODO se il pkt non ha il flag PSH impostato vuol dire che ï¿½ frammentato e devo aspetta gli altri

				outToServer.write(payload,0,20);
				Thread.sleep(200);
				outToServer.write(payload,20,payload.length-20);
				//outToServer.flush();

				// Reading outside-server response
				responseLen = 0;
				long [] value={pktInfo.tcpHeader.acknowledgementNumber,0};
				int count = 0;

				//String pay=new String(payload);

				while ((count = inFromServer.read(response,0,ToyVpnService.MAX_PACKET_LENGTH)) > 0) {
					responseLen += count;
					value = sendToApp(response, count, value);
					response = new byte[ToyVpnService.MAX_PACKET_LENGTH];
					//					Log.i("Thread: "+Thread.currentThread().getId(), "Request: "+ pay.substring(0,pay.indexOf("\n")));
					//					Log.i("Thread: "+Thread.currentThread().getId(), "sequence number: "+value[0]+" - lenght: "+value[1]);
				}


			} catch (IOException e1) {
				e1.printStackTrace();
			}	

			if(responseLen <0){//The server didn't sent anythings. The connection will be closed 
				sendFIN();
				return false;
			}

			//Log.i("ThreadLog",pktInfo.tcpHeader.sourcePort+" - "+ responseLen + " -Response:  " +" \n" + new String(response ) );


			try {
				ssTCP.close();
				sendFIN();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return true;
	}

	private ByteBuffer waitForTotalPayload() {
		int i=0;
		byte[] receivedPacket = new byte[ToyVpnService.MAX_PACKET_LENGTH],payload;
		ByteBuffer totalPacket  = ByteBuffer.allocateDirect(ToyVpnService.MAX_PACKET_LENGTH);
		int length, tot, dimP = 0;
		int payloadReceiveLen=0;
		
		while(true){
			i++;
			/** Read from the pipe for the APP response **/
			tot = 0;
			length = 0;
			byte[] sizeBuff = new byte[4];
			//1. Receive the new packet dimension (4 byte length)
			while(tot<4){
				try {
					length = this.readPipe.read(sizeBuff,tot,4-tot);
				} catch (IOException e) {
					Log.e(Thread.currentThread().getId()+"-"+"ERROR-4", i+"");
					e.printStackTrace();
				} // read the length of the pkt
				if (length <0){
					Log.e(Thread.currentThread().getId()+"-"+"ERROR-1", i+"");
					return null;
				}
				tot += length ;
			}

			tot = 0;
			dimP = ByteBuffer.wrap(sizeBuff).getInt();
			//receive the real pkt
			while(dimP>tot){
				try {
					length = this.readPipe.read(receivedPacket,tot,dimP-tot);
				} catch (IOException e) {
					Log.e(Thread.currentThread().getId()+"-"+"ERROR-3", i+"");
					e.printStackTrace();
				}				
				if (length <0){
					Log.e(Thread.currentThread().getId()+"-"+"ERROR-2", i+"");
					return null;
				}
				tot += length ;
			}		
			
			try {			
				pktInfo = new Packet(ByteBuffer.wrap(receivedPacket,0,dimP));
				payloadReceiveLen = dimP-pktInfo.backingBuffer.position();
		
				payload = new byte[payloadReceiveLen];
				
				pktInfo.backingBuffer.get(payload, 0, payloadReceiveLen);	
				totalPacket.put(payload);
				
				Log.i(Thread.currentThread().getId()+"-"+i,new String(payload)+"\n");//new String(totalPacket.array())).substring(0,totalPacket.position()));
				
				
				if(pktInfo.tcpHeader.isPSH()){
					//Log.i("WaitForTotalPayload",(new String(totalPacket.array())).substring(0,totalPacket.position()));
					return(totalPacket);
				}
				
				
				/**
				 * gestione ack di risposta, vediamo se così almeno ho un riscontro e mi manda il resto del messaggio. vediamo un po
				 */
				manageACK();
					
				
			} catch (UnknownHostException e) {
				Log.e(Thread.currentThread().getId()+"-"+"ERROR-5", i+"");
				e.printStackTrace();
			}
			
		}

	}

	private void sendFIN() {
		pktInfo.swapSourceAndDestination();
		long sequenceNum = pktInfo.tcpHeader.acknowledgementNumber ;//(int) Math.random(); // Random number 
		long ackNum = pktInfo.tcpHeader.sequenceNumber + 1; // increment the seq. num.
		pktInfo.updateTCPBuffer(pktInfo.backingBuffer,(byte) Packet.TCPHeader.RST,0, ackNum, 0);// TODO verificare che ci va

		ByteBuffer bufferFromNetwork = pktInfo.backingBuffer;
		bufferFromNetwork.flip();

		sentoToAppQueue.add(bufferFromNetwork);

	}

	/**
	 * Send an empty ack pkt
	 * @throws UnknownHostException 
	 */
	private void manageACK() throws UnknownHostException {
			
		byte[] dst= new byte[ pktInfo.ip4Header.headerLength+pktInfo.tcpHeader.headerLength];
		pktInfo.backingBuffer.position(0);
		pktInfo.backingBuffer.get(dst, 0, pktInfo.ip4Header.headerLength+pktInfo.tcpHeader.headerLength);

		Packet appPacket= new Packet(ByteBuffer.wrap(dst));
		
		appPacket.updateSourceAndDestination();
		int payloadReceiveLen = pktInfo.backingBuffer.limit()-pktInfo.backingBuffer.position();
		long acknowledgmentNumber = pktInfo.tcpHeader.sequenceNumber + payloadReceiveLen;
		long sequenceNum = pktInfo.tcpHeader.acknowledgementNumber;
		
		
		appPacket.updateTCPBuffer(appPacket.backingBuffer, (byte) Packet.TCPHeader.ACK , sequenceNum, acknowledgmentNumber, 0);

		ByteBuffer bufferFromNetwork = appPacket.backingBuffer;
		bufferFromNetwork.position(0);

		sentoToAppQueue.add(bufferFromNetwork);

	}

	/**
	 * Send the SYN/ACK response
	 */
	public void manageSYN() {
		// Send SYN-ACK response
		pktInfo.swapSourceAndDestination();
		long sequenceNum = random.nextInt(Short.MAX_VALUE + 1) ;//(int) Math.random(); // Random number 
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
		// Send SYN-ACK response
		pktInfo.swapSourceAndDestination();
		long sequenceNum = pktInfo.tcpHeader.acknowledgementNumber ;//(int) Math.random(); // Random number 
		long ackNum = pktInfo.tcpHeader.sequenceNumber + 1; // increment the seq. num.
		pktInfo.updateTCPBuffer(pktInfo.backingBuffer,(byte) (Packet.TCPHeader.FIN | Packet.TCPHeader.ACK | Packet.TCPHeader.PSH), sequenceNum, ackNum, 0);

		ByteBuffer bufferFromNetwork = pktInfo.backingBuffer;
		bufferFromNetwork.flip();

		sentoToAppQueue.add(bufferFromNetwork);


		//		Log.i(TAG, "FIN request");
		//		pktInfo.swapSourceAndDestination();
		//		long sequenceNum = pktInfo.tcpHeader.acknowledgementNumber ;//(int) Math.random(); // Random number 
		//		long ackNum = pktInfo.tcpHeader.sequenceNumber + 1; // increment the seq. num.
		//		pktInfo.updateTCPBuffer(pktInfo.backingBuffer,(byte) (Packet.TCPHeader.ACK), sequenceNum, ackNum, 0);
		//
		//		ByteBuffer bufferFromNetwork = pktInfo.backingBuffer;
		//		bufferFromNetwork.flip();
		//
		//		sentoToAppQueue.add(bufferFromNetwork);
		//		
		//		sequenceNum ++;
		//		pktInfo.updateTCPBuffer(pktInfo.backingBuffer,(byte) (Packet.TCPHeader.FIN), sequenceNum, ackNum, 0);
		//		bufferFromNetwork = pktInfo.backingBuffer;
		//		bufferFromNetwork.flip();
		//		
		//		sentoToAppQueue.add(bufferFromNetwork);

	}










	/**
	 * Open a new connection with the outsideServer
	 * @return The socket descriptor for the connection
	 */
	public Socket openConnection() {
		Socket ssTCP = null;
		//Log.i(TAG,pktInfo.tcpHeader.sourcePort + " - " + " New connection: " + pktInfo.ip4Header.destinationAddress + ":"+ pktInfo.tcpHeader.destinationPort);

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
	private long[] sendToApp(byte[] payloadResponse, int payloadLengthTOT, long []precValue) {

		int payloadLength = payloadLengthTOT;
		int payloadSent = 0;
		int payloadReceiveLen = (pktInfo.backingBuffer.capacity()-pktInfo.ip4Header.headerLength-pktInfo.tcpHeader.headerLength);

		long acknowledgmentNumber = pktInfo.tcpHeader.sequenceNumber + payloadReceiveLen;
		long sequenceNumPrec = precValue[0];

		long sequenceNum = 0;
		long payloadLenghtPrec = precValue[1];

		byte flags;
		while(payloadLength>0){
			if(payloadLength>MAX_MTU){
				payloadSent = MAX_MTU;
				flags = (byte) (Packet.TCPHeader.ACK);
			}
			else {
				payloadSent = payloadLength;
				flags = (byte) ( Packet.TCPHeader.PSH |Packet.TCPHeader.ACK);
			}

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

				sendToAppPkt.updateTCPBuffer(newBuffer, flags, sequenceNum, 
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

			} catch (Exception e) {e.printStackTrace();}

		}	
		return (new long[]{sequenceNum,payloadLenghtPrec});
	}

	public void reassemblyFragment(){

	}

	public boolean checkFragmentation(Packet.IP4Header headerIP){
		if (headerIP.moreFragment)
			return true;
		return false;

	}


}
