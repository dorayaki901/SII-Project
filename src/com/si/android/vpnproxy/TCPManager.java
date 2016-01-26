package com.si.android.vpnproxy;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.net.VpnService;
import android.util.Log;

public class TCPManager {
	private static String TAG = "TCPManager";
	public Packet pktInfo;
	private ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue;
	private VpnService vpn;
	private DataOutputStream outToServer;
	private BufferedReader inFromServer;
	private Socket ssTCP = null;


	public TCPManager(Packet pktInfo, ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue, VpnService vpn) {
		this.pktInfo = pktInfo;
		this.sentoToAppQueue = sentoToAppQueue;
		this.vpn = vpn;

	}

	/**
	 * Main Class for manage the connection
	 * @param payload The TCP packet payload
	 * @return
	 */
	public boolean managePKT(byte[] payload) {

		/** 1 checkout the type of pkt: SYN-SYN/ACK-ACK-FIN **/ 
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
		
		/** 1 If the pkt has payload manage the request (Pure ACK Ignored) **/ 
		char[] responce = new char[ToyVpnService.MAX_PACKET_LENGTH];
		int tot = 0;
		int responceLen = 0;
		
		//if the msg have some payload, resend it to the outside Server
		if(!(new String(payload)).equals("") && payload!= null){
			try {
				if(ssTCP==null){ // only the fist time. If I have already a connection, send directly the payload
					ssTCP = openConnection();
					this.outToServer = new DataOutputStream(ssTCP.getOutputStream());
					this.inFromServer = new BufferedReader(new InputStreamReader(ssTCP.getInputStream()));
				}
				//send request
				Log.i("ThreadLog",pktInfo.tcpHeader.sourcePort+" - "+"Payload sent to server " +  (new String(payload)).isEmpty());

				outToServer.write(payload,0,payload.length);

				Log.i(TAG, "Reading Outside-server responce");
				responceLen = inFromServer.read(responce, tot, ToyVpnService.MAX_PACKET_LENGTH-tot);

			} catch (IOException e1) {
				e1.printStackTrace();
			}	
			//Log.d("ThreadLog",pktInfo.tcpHeader.sourcePort+" - "+ tot + " -Responce:  " +" \n" + new String(responce) );
			sendToApp(new String(responce).getBytes(), responceLen, false);

		}
		return true;
	}
	
	/**
	 * Send the SYN/ACK responce
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
	 * Send the FIN-responce
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
	private void sendToApp(byte[] receiveData, int length, boolean protocol) {
		// TODO 
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

		lengthPacket += (protocol) ? Packet.UDP_HEADER_SIZE : pktInfo.tcpHeader.headerLength;		

		synchronized(this){
			try {
				Log.d("sendToApp", "OLD1:" + pktInfo.toString() + "\n" + new String(pktInfo.backingBuffer.array()));
				pktInfo.backingBuffer.position(0);

				ByteBuffer newBuffer = ByteBuffer.allocateDirect(lengthPacket);
				Packet sendToAppPkt = new Packet(pktInfo.backingBuffer);
				pktInfo.backingBuffer.position(0);
				sendToAppPkt.backingBuffer = null;
				
				int payloadReceive = (pktInfo.backingBuffer.capacity()-pktInfo.ip4Header.headerLength-pktInfo.tcpHeader.headerLength);
				long acknowledgmentNumber = pktInfo.tcpHeader.sequenceNumber + payloadReceive;// +length;
				long sequenceNum = pktInfo.tcpHeader.acknowledgementNumber;
				
				sendToAppPkt.swapSourceAndDestination();

				sendToAppPkt.updateTCPBuffer(newBuffer, (byte) (Packet.TCPHeader.PSH | Packet.TCPHeader.ACK), sequenceNum  , acknowledgmentNumber , length, ByteBuffer.wrap(receiveData));
				
				sendToAppPkt.backingBuffer.position(0);
				ByteBuffer bufferFromNetwork = sendToAppPkt.backingBuffer;

				sentoToAppQueue.add(bufferFromNetwork);
				Log.d("sendToApp1", "bking:" + new String(bufferFromNetwork.array()));

			} catch (Exception e) {e.printStackTrace();}
		}


	}

}
