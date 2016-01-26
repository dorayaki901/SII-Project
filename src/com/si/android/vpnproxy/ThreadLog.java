package com.si.android.vpnproxy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.GZIPInputStream;

import com.si.android.vpnproxy.Packet.TCPHeader;

import android.net.VpnService;
import android.util.Log;

public class ThreadLog implements Runnable {
	FileOutputStream out;
	VpnService vpn;
	Packet pktInfo = null;
	int lengthRequest;
	ByteBuffer packetBuffer;
	private static String TAG = "ThreadLog";
	public boolean connected = false;
	private PipedInputStream readPipe;
	ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue ;
	
	public ThreadLog(FileOutputStream out2, ByteBuffer packet,int length, VpnService vpn,int i, ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue) {
		inizializationPacket(out2, packet, length, vpn, i, sentoToAppQueue);
	}
	
	public void setPipe(PipedInputStream readPipe) {
		this.readPipe=readPipe;		
	}

	private void inizializationPacket(FileOutputStream out2, ByteBuffer packet,int length, VpnService vpn,int i, ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue) {
		this.packetBuffer = ByteBuffer.allocate(length);
		this.lengthRequest=length;
		this.sentoToAppQueue = sentoToAppQueue;

		if (pktInfo==null){
			this.out=out2;
			this.vpn=vpn;
		}

		try {
			packetBuffer.put(packet.array(), 0, length);
			packetBuffer.position(0);
			pktInfo = new Packet(packetBuffer);
		} catch (UnknownHostException e) {e.printStackTrace();}

	}
	/**
	 * MAIN THREAD FUNCTION
	 */
	@Override
	public void run() {

		if(pktInfo.ip4Header.destinationAddress.getHostAddress().equals("160.80.10.11")){
			Log.i(TAG, "Starting new Thread for connection at:" + pktInfo.ip4Header.destinationAddress.getHostAddress() + ":" + pktInfo.tcpHeader.destinationPort);
			TCPSocket();
		}

//				if(pktInfo.isUDP()){
//					UDPSocket();
//					return;
//				}
//				if(pktInfo.isTCP()){
//					TCPSocket();
//					return;
//				}

	}

	/**
	 * Send and receive messages through an UDP connection
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

//				Log.i("ThreadLog", "UDP:"+ new String(payload)+"\nSIZE:"+payload.length);
				Log.i("ThreadLog - UDP", pktInfo.ip4Header.destinationAddress.getHostAddress());


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



	/**
	 * brief Send and receive messages through an TCP connection
	 */
	private void TCPSocket() {
		Socket ssTCP = null;
		char[] responce = new char[ToyVpnService.MAX_PACKET_LENGTH];
		DataOutputStream outToServer = null;
		BufferedReader inFromServer = null;
		Packet pktReply = null;
		connected = false;
		byte[] receivedPacket = new byte[ToyVpnService.MAX_PACKET_LENGTH];
		try {

			//do{
				if(pktInfo == null){
					Log.i(TAG,"PKT NULL");
					//continue;
					return;
				}
				//Extract payload
				byte[] payload = new byte[pktInfo.backingBuffer.remaining()];
				pktInfo.backingBuffer.get(payload, 0, pktInfo.backingBuffer.remaining());

				//Log.i("ThreadLog", i +"packet" + pktInfo.toString());

				/// checkout the type of pkt: SYN-SYN/ACK-ACK-FIN 
				//SYN pkt
				if (pktInfo.tcpHeader.isSYN() && !pktInfo.tcpHeader.isACK()){
					Log.d("ThreadLog",pktInfo.tcpHeader.sourcePort+" - "+"SYN pkt received");
					pktReply = SYN_ACKresponse(pktInfo);
					// Send SYN-ACK response
					ByteBuffer bufferFromNetwork = pktReply.backingBuffer;
					 bufferFromNetwork.flip();
					
					sentoToAppQueue.add(bufferFromNetwork);
				}

				//ACK pkt
				if (pktInfo.tcpHeader.isACK() && !pktInfo.tcpHeader.isSYN()){
					Log.i("ThreadLog",pktInfo.tcpHeader.sourcePort+" - "+"ACK pkt received " + new String(payload));
					//pktReply = SYN_ACKresponse(pktInfo);
				}
				
				
				//if the msg have some payload, resend it to the outside Server
				if(!(new String(payload)).equals("") && payload!= null){
					//Log.i(TAG,i + " pkt:  " + new String(payload));

					if(ssTCP==null){ // only the fist time. If I have already a connection, send directly the payload
						Log.i(TAG,pktInfo.tcpHeader.sourcePort+" - "+" NEW CONNECTION TO: " + pktInfo.ip4Header.destinationAddress + ":"+ pktInfo.tcpHeader.destinationPort);

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
					Log.i("ThreadLog",pktInfo.tcpHeader.sourcePort+" - "+"Payload sent to server " +  (new String(payload)).isEmpty());

					outToServer.write(payload,0,payload.length);

					//receive the response
					//char[] buff = new char[6000];
					
					
					int n = 0;
					//n = inFromServer.read(buff, 0, buff.length);
					Log.i("ThreadLog",pktInfo.tcpHeader.sourcePort+" - "+"Pre read line");
//					String line = inFromServer.readLine();
//					Log.i("ThreadLog","Post read line");
//					responce = "";
//					while(line!=null && !line.equals("")){
//						responce += line;
//						//Log.d("ThreadLog","PROGRESS RESPONNCE: "+responce);
//						line = inFromServer.readLine();
//					}
					
					int tot = 0;
					int length = 1;
					Log.i(TAG, "STO LEGGENDO");
					//while(length>0){
						length = inFromServer.read(responce, tot, ToyVpnService.MAX_PACKET_LENGTH-tot);				
					//	if (length <0){
					//		Log.i(TAG, "Error reading from socket");
					//		return;
					//	}
					//	Log.i(TAG, "LEN: " + length + "TOT");
					//	tot += length ;
					//}		
					
					
					
					//responce = new String(buff);
					Log.d("ThreadLog",pktInfo.tcpHeader.sourcePort+" - "+ tot + " -Responce:  " +" \n" + new String(responce) );
					sendToApp(new String(responce).getBytes(),length,false);
					

				}
				//Read from the pipe for the response
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
////				pktInfo = new Packet(ByteBuffer.wrap(receivedPacket));
//
//				Log.i(TAG,pktInfo.tcpHeader.sourcePort+" - "+"Read From Pipe: " + tot + " -- " + new String(packetBuffer.array()));
				
				//Thread.sleep(200);
				
			//}while(true);

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
	private Packet SYN_ACKresponse(Packet syn_pkt) {
		int payloadSize = syn_pkt.backingBuffer.limit() - syn_pkt.backingBuffer.position();
		byte flags = syn_pkt.tcpHeader.flags;

		flags = BitUtils.setBit(1, 2, flags); // Syn bit
		flags = BitUtils.setBit(1, 5, flags); // Ack bit

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
	 * 
	 * @param syn_pkt
	 * @param payload
	 * @return
	 */
	//TODO
	private Packet FINresponse(Packet syn_pkt) {
		int payloadSize = syn_pkt.backingBuffer.limit() - syn_pkt.backingBuffer.position();
		byte flags = syn_pkt.tcpHeader.flags;

		flags = BitUtils.setBit(1, 2, flags); // Syn bit
		flags = BitUtils.setBit(1, 5, flags); // Ack bit

		long sequenceNum = 1 ;//(int) Math.random(); // Random number 
		long ackNum = syn_pkt.tcpHeader.sequenceNumber + 1; // increment the seq. num.

		//Log.i("ThreadLog", syn_pkt.tcpHeader.flags + ": "+ Integer.toBinaryString(Integer.valueOf(flags)));
		ByteBuffer buffer = syn_pkt.backingBuffer;
		buffer.position(0);
		syn_pkt.updateTCPBuffer(buffer, flags, sequenceNum, ackNum, payloadSize);
		syn_pkt.updateSourceAndDestination();

		return syn_pkt;
	}

	/** 
	 * Send to app the incoming packet
	 * @param receiveData 
	 * @param length
	 * @param protocol
	 */
	private void sendToApp(byte[] receiveData,int length, boolean protocol)  {
		//String s="<html><head><title>Benvenuto</title></head><body><div align=\"center\"><font size=\"6\">Hello World!</font></div></body></html>";
		//String appoggio="HTTP/1.1 500 Internal Server Error\r\nContent-Length: 9051\r\nContent-Type: application/soap+xml; charset=utf-8\r\nServer: Microsoft-HTTPAPI/2.0\r\n\r\n<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:a=\"http://www.w3.org/2005/08/addressing\"><s:Header><a:Action s:mustUnderstand=\"1\">http://schemas.microsoft.com/net/2005/12/windowscommunicationfoundation/dispatcher/fault</a:Action></s:Header><s:Body><s:Fault><s:Code><s:Value>s:Receiver</s:Value><s:Subcode><s:Value xmlns:a=\"http://schemas.microsoft.com/net/2005/12/windowscommunicationfoundation/dispatcher\">a:InternalServiceFault</s:Value></s:Subcode></s:Code><s:Reason><s:Text xml:lang=\"it-IT\">Unable to obtain service fault fromexception: System.ServiceModel.Security.SecurityAccessDeniedException: Provided credentials are not valid.&#xD;   at Grep.ManagementPlatform.Services.Business.Impl.ThrowOnFailureAuthenticationProvider.Throw() in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Business\\Impl\\ThrowOnFailureAuthenticationProvider.cs:line 42&#xD;   at Grep.ManagementPlatform.Services.Business.Impl.ThrowOnFailureAuthenticationProvider.ValidateCustomerCredentials(ServiceCredentialsDto credentials) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Business\\Impl\\ThrowOnFailureAuthenticationProvider.cs:line 30&#xD;   at Grep.ManagementPlatform.Services.Impl.ComponentServiceImpl.AuthenticateCustomerAccount(ServiceCredentialsDto credentials) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Impl\\ComponentServiceImpl.cs:line 104&#xD;   at Castle.Proxies.Invocations.IComponentService_AuthenticateCustomerAccount.InvokeMethodOnTarget()&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Data.Impl.NHibernate.Integration.NHTransactionIntegration.NHibernateTransactionInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Data.Impl.NHibernate\\Integration\\NHTransactionIntegration\\NHibernateTransactionInterceptor.cs:line 62&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Castle.Facilities.AutoTx.TransactionInterceptor.SynchronizedCase(IInvocation invocation, ITransaction transaction) in d:\\BuildAgent-03\\work\\9844bdf039249947\\src\\Castle.Facilities.AutoTx\\TransactionInterceptor.cs:line 179&#xD;   at Castle.Facilities.AutoTx.TransactionInterceptor.Castle.DynamicProxy.IInterceptor.Intercept(IInvocation invocation) in d:\\BuildAgent-03\\work\\9844bdf039249947\\src\\Castle.Facilities.AutoTx\\TransactionInterceptor.cs:line 119&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Commons.Integration.Interceptors.ServiceLoggingInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\Integration\\Interceptors\\ServiceLoggingInterceptor.cs:line 33&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Commons.Integration.Interceptors.ServiceExceptionHandlingInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\Integration\\Interceptors\\ServiceExceptionHandlingInterceptor.cs:line 33</s:Text></s:Reason><s:Detail><ExceptionDetail xmlns=\"http://schemas.datacontract.org/2004/07/System.ServiceModel\" xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\"><HelpLink i:nil=\"true\"/><InnerException><HelpLink>groups.google.com/group/castle-project-users</HelpLink><InnerException i:nil=\"true\"/><Message>No component for supporting the service Grep.ManagementPlatform.Commons.ExceptionManagement.ServiceFaultAssemblers.DefaultServiceFaultAssembler was found</Message><StackTrace>   at Castle.MicroKernel.DefaultKernel.Castle.MicroKernel.IKernelInternal.Resolve(Type service, IDictionary arguments, IReleasePolicy policy)&#xD;   at Castle.Facilities.TypedFactory.TypedFactoryComponentResolver.Resolve(IKernelInternal kernel, IReleasePolicy scope)&#xD;   at Castle.Facilities.TypedFactory.Internal.TypedFactoryInterceptor.Resolve(IInvocation invocation)&#xD;   at Castle.Facilities.TypedFactory.Internal.TypedFactoryInterceptor.Intercept(IInvocation invocation)&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Castle.Proxies.IServiceFaultAssemblerFactoryProxy.GetServiceFaultAssembler(Exception e)&#xD;   at Grep.ManagementPlatform.Commons.ExceptionManagement.ServiceFaultAssemblerHelper.AssembleFault(IServiceFaultAssemblerFactory factory, Exception e) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\ExceptionManagement\\ServiceFaultAssemblerHelper.cs:line 14</StackTrace><Type>Castle.MicroKernel.ComponentNotFoundException</Type></InnerException><Message>Unable to obtain service fault from exception: System.ServiceModel.Security.SecurityAccessDeniedException: Provided credentials are not valid.&#xD;   at Grep.ManagementPlatform.Services.Business.Impl.ThrowOnFailureAuthenticationProvider.Throw() in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Business\\Impl\\ThrowOnFailureAuthenticationProvider.cs:line 42&#xD;   at Grep.ManagementPlatform.Services.Business.Impl.ThrowOnFailureAuthenticationProvider.ValidateCustomerCredentials(ServiceCredentialsDto credentials) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Business\\Impl\\ThrowOnFailureAuthenticationProvider.cs:line 30&#xD;   at Grep.ManagementPlatform.Services.Impl.ComponentServiceImpl.AuthenticateCustomerAccount(ServiceCredentialsDto credentials) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Impl\\ComponentServiceImpl.cs:line 104&#xD;   at Castle.Proxies.Invocations.IComponentService_AuthenticateCustomerAccount.InvokeMethodOnTarget()&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Data.Impl.NHibernate.Integration.NHTransactionIntegration.NHibernateTransactionInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Data.Impl.NHibernate\\Integration\\NHTransactionIntegration\\NHibernateTransactionInterceptor.cs:line 62&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Castle.Facilities.AutoTx.TransactionInterceptor.SynchronizedCase(IInvocation invocation, ITransaction transaction) in d:\\BuildAgent-03\\work\\9844bdf039249947\\src\\Castle.Facilities.AutoTx\\TransactionInterceptor.cs:line 179&#xD;   at Castle.Facilities.AutoTx.TransactionInterceptor.Castle.DynamicProxy.IInterceptor.Intercept(IInvocation invocation) in d:\\BuildAgent-03\\work\\9844bdf039249947\\src\\Castle.Facilities.AutoTx\\TransactionInterceptor.cs:line 119&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Commons.Integration.Interceptors.ServiceLoggingInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\Integration\\Interceptors\\ServiceLoggingInterceptor.cs:line 33&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Commons.Integration.Interceptors.ServiceExceptionHandlingInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\Integration\\Interceptors\\ServiceExceptionHandlingInterceptor.cs:line 33</Message><StackTrace>   at Grep.ManagementPlatform.Commons.ExceptionManagement.ServiceFaultAssemblerHelper.AssembleFault(IServiceFaultAssemblerFactory factory, Exception e) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\ExceptionManagement\\ServiceFaultAssemblerHelper.cs:line 23&#xD;   at Grep.ManagementPlatform.Commons.Integration.Interceptors.ServiceExceptionHandlingInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\Integration\\Interceptors\\ServiceExceptionHandlingInterceptor.cs:line 64&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Castle.Proxies.IComponentServiceProxy.AuthenticateCustomerAccount(ServiceCredentialsDto credentials)&#xD;   at SyncInvokeAuthenticateCustomerAccount(Object , Object[] , Object[] )&#xD;   at System.ServiceModel.Dispatcher.SyncMethodInvoker.Invoke(Object instance, Object[] inputs, Object[]&amp; outputs)&#xD;   at System.ServiceModel.Dispatcher.DispatchOperationRuntime.InvokeBegin(MessageRpc&amp; rpc)&#xD;   at System.ServiceModel.Dispatcher.ImmutableDispatchRuntime.ProcessMessage5(MessageRpc&amp; rpc)&#xD;   at System.ServiceModel.Dispatcher.ImmutableDispatchRuntime.ProcessMessage31(MessageRpc&amp; rpc)&#xD;   at System.ServiceModel.Dispatcher.MessageRpc.Process(Boolean isOperationContextSet)</StackTrace><Type>System.InvalidOperationException</Type></ExceptionDetail></s:Detail></s:Fault></s:Body></s:Envelope>";
		String s = "<html><head><title>404 Not Found</title></head><body><h1>ppppppppppppppppppppppppppppppppppppppppppppppppppppppphhhhhhhhhhhhhhhhhhhhhhhhhhNot Foundsddddddddddddddddxxxe</h1><hr></body></html>";
		//String s = "<html><head><title>404 Not Found</title></head><body><h1>Not Found</h1><p>The requested URL /ciao was not found on this server.</p><hr><address>Apache/2.2.22 (Debian) Server at 160.80.10.11 Port 80</address></body></html>";

		String appoggio = "HTTP/1.1 200 OK\r\nContent-Length: " + s.length() + "\r\nContent-Type: text/html; charset=utf-8\r\nServer: Microsoft-HTTPAPI/2.0\r\n\r\n"+s;
		receiveData = appoggio.getBytes();
		
		length = appoggio.length();
		Log.d("sendToApp", "LENGHT :" + length);
		Log.d("paket P","payload:" + new String(receiveData));
		ByteBuffer bufferToSend = null;
		Packet pktToSend = null;
		int lengthPacket = length + pktInfo.ip4Header.headerLength;
		//Log.d("sendToApp", "LENGHT PACK NEW:" + lengthPacket + "ip4Header.headerLength " + pktInfo.ip4Header.headerLength + " pktInfo.backingBuffer.array().length" + pktInfo.backingBuffer.array().length);

		//Log.d("sendToApp", "ORIGINAL:" + pktInfo.toString());
		//Log.d("sendToApp", "ORIGINAL: len: " + length + new String(pktInfo.backingBuffer.array()));
		
		lengthPacket += (protocol) ? Packet.UDP_HEADER_SIZE : pktInfo.tcpHeader.headerLength;		
//		bufferToSend =  ByteBuffer.allocate(lengthPacket);
//
//		if (pktInfo.backingBuffer.array().length>lengthPacket)
//			bufferToSend.put(pktInfo.backingBuffer.array(),0, lengthPacket);
//		else
//			bufferToSend.put(pktInfo.backingBuffer.array());// QUI OK
//
//		try {
//			bufferToSend.position(0);
//			pktToSend = new Packet(bufferToSend);
//		} catch (UnknownHostException e1) {	e1.printStackTrace(); }
//
//		pktToSend.updateSourceAndDestination();
//
//		try{
//
//			if (protocol){
//				pktToSend.updateUDPBuffer(receiveData,length);
//			}
//				pktToSend.updateTCPBuffer(receiveData,length);

//		}catch (Exception e1) {	e1.printStackTrace(); }
	
		synchronized(this){
			try {
				Log.d("sendToApp", "OLD1:" + pktInfo.toString() + "\n" + new String(pktInfo.backingBuffer.array()));
				pktInfo.backingBuffer.position(0);
			
				//pktInfo.backingBuffer = a;
				
				ByteBuffer newBuffer = ByteBuffer.allocateDirect(lengthPacket);
				Packet prova = new Packet(pktInfo.backingBuffer);
				pktInfo.backingBuffer.position(0);
				prova.backingBuffer = null;
				long acknowledgmentNumber = pktInfo.tcpHeader.sequenceNumber + (pktInfo.backingBuffer.capacity()-40);// +length;
				long sequenceNum = pktInfo.tcpHeader.acknowledgementNumber;
				prova.swapSourceAndDestination();
				//prova.ip4Header.identificationAndFlagsAndFragmentOffset = Integer.parseInt("0111110001111010100000000000000",2);;
				
				prova.updateTCPBuffer(newBuffer, (byte) (Packet.TCPHeader.PSH | Packet.TCPHeader.ACK), sequenceNum  , acknowledgmentNumber , length, ByteBuffer.wrap(appoggio.getBytes()));
				prova.backingBuffer.position(0);
				//pktToSend.backingBuffer.position(0);
				//ByteBuffer bufferFromNetwork = pktToSend.backingBuffer;
				ByteBuffer bufferFromNetwork = prova.backingBuffer;

				sentoToAppQueue.add(bufferFromNetwork);
				Log.d("sendToApp1", "NEW2:" + (pktInfo.backingBuffer.capacity()-40) +(new String(prova.backingBuffer.array())).length() + prova.toString() + " " + new String(prova.backingBuffer.array()));
				Log.d("sendToApp1", "OLD2:" + pktInfo.toString() + "\n" + new String(pktInfo.backingBuffer.array()));

				//pktToSend.backingBuffer.position(0);
				//out.write(pktToSend.backingBuffer.array());
				
				//out.write(pktToSend.backingBuffer.array(), 0, 300);
				//out.write(pktToSend.backingBuffer.array(), 300, pktToSend.ip4Header.totalLength - 300);

//				pktToSend.backingBuffer.position(0);
				//Log.d("sendToApp1", "NEW:" + pktToSend.toString() + "\n" + new String(pktToSend.backingBuffer.array()));
				//Log.d("sendToApp1", "OLD:" + pktInfo.toString() + "\n" + new String(pktInfo.backingBuffer.array()));

//				//printGzipBody(receiveData);
//				Log.d("sendToApp","SEND TO APP: " + pktToSend.ip4Header.totalLength + ":" + (new String(pktToSend.backingBuffer.array())).substring(0, 427));
				
//				out.write(pktToSend.backingBuffer.array(), 0, pktToSend.backingBuffer.array().length);
			} catch (Exception e) {e.printStackTrace();}
		}
			
	}
	
	void printGzipBody(byte[] bytes) throws IOException{
			String app = new String(bytes);
			String app2 = null;
			int pos = app.lastIndexOf("\n");
			app2 = app.substring(pos,app.length()-1);
			Log.i("sendToApp",pos + " " + app);
			Log.i("sendToApp",app2);
			byte[] B2 = null;
			byte[] fileBytes = new byte[bytes.length - pos];
			for(int i =0;i<bytes.length - pos;i++)
				fileBytes[i] = bytes[i+pos];
			Log.i("sendToApp",new String(fileBytes));
	        GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(fileBytes));
	        BufferedReader bf = new BufferedReader(new InputStreamReader(gis, "UTF-8"));
	        String outStr = "";
	        String line;
	        while ((line=bf.readLine())!=null) {
	          outStr += line;
	        }
	        Log.i("sendToApp", outStr);
		
	}
	


}
