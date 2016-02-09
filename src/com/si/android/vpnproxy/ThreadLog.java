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

	public ThreadLog(FileOutputStream out, ByteBuffer pktFromApp, int lengthRequest, VpnService vpn, ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue) throws UnknownHostException {
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
		
		//byte[] receivedPacket = new byte[ToyVpnService.MAX_PACKET_LENGTH];
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
	
		byte[] payload = null;
		TCPManager TCP = new TCPManager(this.pktInfo, this.sentoToAppQueue,this.vpn,this.readPipe);
						
		try {
			
			if(this.pktInfo == null){
				Log.e(TAG,"error pkt null");
				return;
			}

			payload = new byte[pktInfo.backingBuffer.remaining()];			
			
			pktInfo.backingBuffer.get(payload, 0, pktInfo.backingBuffer.remaining());

			//Manager TCP connection and TCP pkt
			if(!TCP.managePKT(payload))
				return;
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}


}
