package com.si.android.vpnproxy;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.net.VpnService;
import android.util.Log;

public class ThreadLog implements Runnable {
	private static String TAG = "ThreadLog";
	FileOutputStream out;
	VpnService vpn;
	Packet pktInfo = null;
	int lengthRequest;
	private PipedInputStream readPipe;
	ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue ;
	private ByteBuffer packetBuffer;

	public ThreadLog(FileOutputStream out, ByteBuffer pktFromApp, int lengthRequest, VpnService vpn,int i, ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue) throws UnknownHostException {
		this.lengthRequest = lengthRequest;
		this.vpn = vpn;
		this.out = out;
		this.sentoToAppQueue = sentoToAppQueue;
		
		this.packetBuffer = ByteBuffer.allocate(lengthRequest);
		this.packetBuffer.put( pktFromApp.array(), 0, lengthRequest);
		this.packetBuffer.position(0);
		this.pktInfo = new Packet(packetBuffer);
	}

	public void setPipe(PipedInputStream readPipe) {
		this.readPipe = readPipe;		
	}

	/**
	 * MAIN THREAD FUNCTION
	 */
	@Override
	public void run() {

		if(pktInfo.isUDP()){
				try {
					UDPSocket();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}	
			return;
		}

		if(pktInfo.isTCP()){
			TCPSocket();
			return;
		}

	}

	/**
	 * Send and receive messages through an UDP connection
	 * @throws IOException 
	 */
	private void UDPSocket() throws IOException {
		
		byte[] receivedPacket = new byte[ToyVpnService.MAX_PACKET_LENGTH];
		UDPManager UDP = new UDPManager(this.pktInfo, sentoToAppQueue, vpn);


		byte[] payload = new byte[lengthRequest-pktInfo.ip4Header.headerLength-Packet.UDP_HEADER_SIZE];
		//			Log.i("ThreadLog", "UDP:"+ lengthRequest+"\nSIZE:"+pktInfo.ip4Header.headerLength);
		pktInfo.backingBuffer.position(pktInfo.ip4Header.headerLength+Packet.UDP_HEADER_SIZE);
		pktInfo.backingBuffer.get(payload, 0, lengthRequest-pktInfo.ip4Header.headerLength-Packet.UDP_HEADER_SIZE);

		UDP.managePKT(payload);

		//TODO !!!!!!!!SECONDO ME NON NECESSARIO PER UDP!!!!!!!!!!!!-->Se usato aggiungere ciclo
		//Wait for the Pipe response
		//			Log.i("ThreadLog", "post send to app");
		//			try {
		//				lengthRequest=readPipe.read(receivedPacket);
		//				pktInfo = new Packet(ByteBuffer.wrap(receivedPacket));
		//
		//				Log.i("ThreadLog - Lunghezza", lengthRequest+"");
		//			} catch (IOException e) {
		//				e.printStackTrace();
		//			}	

	}



	/**
	 * Send and receive messages through an TCP connection
	 */
	private void TCPSocket() {
		byte[] receivedPacket = new byte[ToyVpnService.MAX_PACKET_LENGTH];
		byte[] payload = null;
		TCPManager TCP = new TCPManager(this.pktInfo, this.sentoToAppQueue,this.vpn,this.readPipe);
				
		
		
		try {

			//			do{
			if(this.pktInfo == null){
				Log.e(TAG,"error pkt null");
				//continue;
				return;
			}
			//Extract payload
			payload = new byte[pktInfo.backingBuffer.remaining()];
			pktInfo.backingBuffer.get(payload, 0, pktInfo.backingBuffer.remaining());

			//Manager TCP connection and TCP pkt
			if(!TCP.managePKT(payload))
				return;

			//				/** Read from the pipe for the APP response **/
			//				int length = 0;
			//				int dimP = 0;
			//				int tot = 0;
			//				byte[] sizeBuff = new byte[4];
			//				//1. Receive the new packet dimension (4 byte length)
			//				while(tot<4){
			//					length = readPipe.read(sizeBuff,tot,4-tot); // read the length of the pkt
			//					if (length <0){
			//						Log.i(TAG, "Error reading from pipe");
			//						return;
			//					}
			//					tot += length ;
			//				}
			//
			//				tot = 0;
			//				dimP = ByteBuffer.wrap(sizeBuff).getInt();
			//				//receive the real pkt
			//				while(dimP>tot){
			//					length = readPipe.read(receivedPacket,tot,dimP-tot);				
			//					if (length <0){
			//						Log.i(TAG, "Error reading from pipe");
			//						return;
			//					}
			//					tot += length ;
			//				}								
			//
			//				//Initializing the new pkt
			//				ByteBuffer packetBuffer = ByteBuffer.allocate(tot);
			//				packetBuffer.put(receivedPacket, 0, tot);
			//				packetBuffer.flip();
			//				pktInfo  = new Packet(packetBuffer);
			//				//				this.pktInfo = new Packet(ByteBuffer.wrap(receivedPacket));
			//
			//				Log.i(TAG,pktInfo.tcpHeader.sourcePort+" - "+"Read From Pipe: " + tot + " -- " + new String(packetBuffer.array()));
			//				TCP.pktInfo = pktInfo;
			//				//Thread.sleep(200);
			//
			//			}while(true);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}


}
