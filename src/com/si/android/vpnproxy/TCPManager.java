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
import java.util.ArrayList;
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
	long prev;

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

		ByteBuffer totalPayload = ByteBuffer.allocate(ToyVpnService.MAX_PACKET_LENGTH);
		totalPayload.put(payload);

		/** 1. check the type of pkt: SYN-SYN/ACK-FIN **/
		if (pktInfo.tcpHeader.isRST()){ // Reset by peer
			Log.i("TCPManager", "Reset by peer");
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
//			Log.i("Request", pktInfo.tcpHeader.sourcePort+" - "+(new String(payload)));
//			Log.i("Request", pktInfo.tcpHeader.sourcePort+" - "+(new String(pktInfo.backingBuffer.array())));

			/**********************************************************/
			/****Management of fragmentation request ****/
			if(!pktInfo.tcpHeader.isPSH()){
				totalPayload = waitForTotalPayload(totalPayload);
				if(totalPayload == null)
					return false;
			}	

			/**********************************************************/

			try {
				if(ssTCP==null){ // only the fist time. If I have already a connection, send directly the payload
					ssTCP = openConnection();
					this.outToServer = new DataOutputStream(ssTCP.getOutputStream());
					this.inFromServer  = ssTCP.getInputStream();
				}

				//TODO se il pkt non ha il flag PSH impostato vuol dire che � frammentato e devo aspetta gli altri

				outToServer.write(totalPayload.array(),0,totalPayload.limit());
				outToServer.flush();
				//CustomLog.i("Request1 WRITE- "+totalPayload.limit(), (new String(totalPayload.array())).substring(0,totalPayload.limit()));

				// Reading outside-server response
				responseLen = 0;
				
				//aggiunto terzo elemento del vettore
				long [] value={pktInfo.tcpHeader.acknowledgementNumber,0, pktInfo.tcpHeader.sequenceNumber +  pktInfo.backingBuffer.remaining()};
				int count = 0;

				//String pay=new String(payload);
//				ByteBuffer wait=ByteBuffer.allocateDirect(ToyVpnService.MAX_PACKET_LENGTH);
				while ((count = inFromServer.read(response,0,ToyVpnService.MAX_PACKET_LENGTH)) > 0) {
//					wait.put(response, 0, count);
					responseLen += count;
					value = sendToApp(response, count, value);
				}
					//sendToApp(wait.array(), responseLen, value);


			} catch (IOException e1) {
				e1.printStackTrace();
			}	

			if(responseLen <=0){//The server didn't sent anythings. The connection will be closed 
			//	sendFIN();
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

	private ByteBuffer waitForTotalPayload(ByteBuffer totalPayload) {
		
		int i=0;
		ArrayList<Long> sequenceNumberList=new ArrayList<Long>();
		sequenceNumberList.add(pktInfo.tcpHeader.sequenceNumber);
		
		byte[] receivedPacket = new byte[ToyVpnService.MAX_PACKET_LENGTH],
				payload;

		int length, 
		tot, 
		dimP = 0;
		int payloadReceiveLen=0;
		
		while(true){
			CustomLog.i("ID-"+Thread.currentThread().getId(), new String(totalPayload.array()));

			/** Read from the pipe for the APP response **/
			tot = 0;
			length = 0;
			byte[] sizeBuff = new byte[4];
			//1. Receive the new packet dimension (4 byte length)
			while(tot<4){
				try {
					length = this.readPipe.read(sizeBuff,tot,4-tot);
				} catch (IOException e) {
					e.printStackTrace();
				} // read the length of the pkt
				if (length <0)
					return null;

				tot += length ;
			}

			tot = 0;
			dimP = ByteBuffer.wrap(sizeBuff).getInt();
			//receive the real pkt
			while(dimP>tot){
				try {
					length = this.readPipe.read(receivedPacket,tot,dimP-tot);
				} catch (IOException e) {
					e.printStackTrace();
				}				
				if (length <0)
					return null;

				tot += length ;
			}		
			CustomLog.i("ID Payload Pipe-"+Thread.currentThread().getId(), new String(receivedPacket));

			try {			

				
				pktInfo = new Packet(ByteBuffer.wrap(receivedPacket,0,dimP));
				
				boolean present=false;
				for(i=0;i<sequenceNumberList.size();i++)
					if (sequenceNumberList.get(i)==pktInfo.tcpHeader.sequenceNumber)
						present=true;
				
				payloadReceiveLen = pktInfo.backingBuffer.remaining();

				
				if(!present){
					payload = new byte[payloadReceiveLen];
					
					int appPosition=pktInfo.backingBuffer.position();
					
					pktInfo.backingBuffer.get(payload, 0, payloadReceiveLen);	
					
					pktInfo.backingBuffer.position(appPosition);
					
					totalPayload.put(payload);	
					
					
				}
				
				if(pktInfo.tcpHeader.isPSH()){
					CustomLog.i("ID-PSH"+Thread.currentThread().getId(), new String(totalPayload.array()));
					return(totalPayload);
				}
					
				manageACK(payloadReceiveLen);

			} catch (UnknownHostException e) {
				e.printStackTrace();
			}

		}

	}

	//Probabilmente � sbagliato il modo di fare l update dei parametri
	private void sendFIN() {
		pktInfo.swapSourceAndDestination();
//		long sequenceNum = pktInfo.tcpHeader.acknowledgementNumber ;//(int) Math.random(); // Random number 
//		long ackNum = pktInfo.tcpHeader.sequenceNumber + 1; // increment the seq. num.
		pktInfo.updateTCPBuffer(pktInfo.backingBuffer,(byte) Packet.TCPHeader.FIN, pktInfo.tcpHeader.acknowledgementNumber+1, 0, 0);// TODO verificare che ci va

		ByteBuffer bufferFromNetwork = pktInfo.backingBuffer.duplicate();
		bufferFromNetwork.flip();

		sentoToAppQueue.add(bufferFromNetwork);

	}

	/**
	 * Send an empty ack pkt
	 * @throws UnknownHostException 
	 */
	private void manageACK(int payloadReceiveLen) throws UnknownHostException {
		
		Packet appPacket=pktInfo;

		appPacket.swapSourceAndDestination();
		long acknowledgmentNumber = appPacket.tcpHeader.sequenceNumber + appPacket.backingBuffer.remaining();
		Log.i("check remaining", appPacket.backingBuffer.remaining()+" - "+payloadReceiveLen);
		long sequenceNum = appPacket.tcpHeader.acknowledgementNumber;

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
	 * che casino, messo la update e tolto PSH
	 */
	public void manageFIN() {
		// Send SYN-ACK response

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
				Packet sendToAppPkt = new Packet(pktInfo.backingBuffer);// cos� ha lo stesso header
				pktInfo.backingBuffer.position(0);
				sendToAppPkt.backingBuffer = null; //campi riempiti ma nne 

				//Calculating the new ack/sqn numbers
				sequenceNum = sequenceNumPrec  + payloadLenghtPrec;

				//Update the new packet with the right fields
				sendToAppPkt.swapSourceAndDestination();

				sendToAppPkt.updateTCPBuffer(newBuffer, flags, sequenceNum, 
						precValue[2] , payloadSent , (payloadResponse), payloadLengthTOT-payloadLength);
				sendToAppPkt.backingBuffer.position(0);

				//Send the new packet through the out channel
				ByteBuffer bufferFromNetwork = sendToAppPkt.backingBuffer;
				synchronized(this){
					sentoToAppQueue.add(bufferFromNetwork);
				}

				sequenceNumPrec = sequenceNum;
				payloadLenghtPrec = payloadSent;
				payloadLength-= payloadSent;
				prev=sequenceNum+payloadSent;
			} catch (Exception e) {e.printStackTrace();}

		}	
		pktInfo.tcpHeader.sequenceNumber=precValue[2];
		pktInfo.tcpHeader.acknowledgementNumber=sequenceNum+payloadSent;
		return (new long[]{sequenceNum,payloadLenghtPrec,precValue[2]});
	}

	public void reassemblyFragment(){

	}

	public boolean checkFragmentation(Packet.IP4Header headerIP){
		if (headerIP.moreFragment)
			return true;
		return false;

	}


}
