package com.example.android.vpnproxy;

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
		// TODO 
		//String s="<html><head><title>Benvenuto</title></head><body><div align=\"center\"><font size=\"6\">Hello World!</font></div></body></html>";
		//String appoggio="HTTP/1.1 500 Internal Server Error\r\nContent-Length: 9051\r\nContent-Type: application/soap+xml; charset=utf-8\r\nServer: Microsoft-HTTPAPI/2.0\r\n\r\n<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:a=\"http://www.w3.org/2005/08/addressing\"><s:Header><a:Action s:mustUnderstand=\"1\">http://schemas.microsoft.com/net/2005/12/windowscommunicationfoundation/dispatcher/fault</a:Action></s:Header><s:Body><s:Fault><s:Code><s:Value>s:Receiver</s:Value><s:Subcode><s:Value xmlns:a=\"http://schemas.microsoft.com/net/2005/12/windowscommunicationfoundation/dispatcher\">a:InternalServiceFault</s:Value></s:Subcode></s:Code><s:Reason><s:Text xml:lang=\"it-IT\">Unable to obtain service fault fromexception: System.ServiceModel.Security.SecurityAccessDeniedException: Provided credentials are not valid.&#xD;   at Grep.ManagementPlatform.Services.Business.Impl.ThrowOnFailureAuthenticationProvider.Throw() in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Business\\Impl\\ThrowOnFailureAuthenticationProvider.cs:line 42&#xD;   at Grep.ManagementPlatform.Services.Business.Impl.ThrowOnFailureAuthenticationProvider.ValidateCustomerCredentials(ServiceCredentialsDto credentials) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Business\\Impl\\ThrowOnFailureAuthenticationProvider.cs:line 30&#xD;   at Grep.ManagementPlatform.Services.Impl.ComponentServiceImpl.AuthenticateCustomerAccount(ServiceCredentialsDto credentials) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Impl\\ComponentServiceImpl.cs:line 104&#xD;   at Castle.Proxies.Invocations.IComponentService_AuthenticateCustomerAccount.InvokeMethodOnTarget()&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Data.Impl.NHibernate.Integration.NHTransactionIntegration.NHibernateTransactionInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Data.Impl.NHibernate\\Integration\\NHTransactionIntegration\\NHibernateTransactionInterceptor.cs:line 62&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Castle.Facilities.AutoTx.TransactionInterceptor.SynchronizedCase(IInvocation invocation, ITransaction transaction) in d:\\BuildAgent-03\\work\\9844bdf039249947\\src\\Castle.Facilities.AutoTx\\TransactionInterceptor.cs:line 179&#xD;   at Castle.Facilities.AutoTx.TransactionInterceptor.Castle.DynamicProxy.IInterceptor.Intercept(IInvocation invocation) in d:\\BuildAgent-03\\work\\9844bdf039249947\\src\\Castle.Facilities.AutoTx\\TransactionInterceptor.cs:line 119&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Commons.Integration.Interceptors.ServiceLoggingInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\Integration\\Interceptors\\ServiceLoggingInterceptor.cs:line 33&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Commons.Integration.Interceptors.ServiceExceptionHandlingInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\Integration\\Interceptors\\ServiceExceptionHandlingInterceptor.cs:line 33</s:Text></s:Reason><s:Detail><ExceptionDetail xmlns=\"http://schemas.datacontract.org/2004/07/System.ServiceModel\" xmlns:i=\"http://www.w3.org/2001/XMLSchema-instance\"><HelpLink i:nil=\"true\"/><InnerException><HelpLink>groups.google.com/group/castle-project-users</HelpLink><InnerException i:nil=\"true\"/><Message>No component for supporting the service Grep.ManagementPlatform.Commons.ExceptionManagement.ServiceFaultAssemblers.DefaultServiceFaultAssembler was found</Message><StackTrace>   at Castle.MicroKernel.DefaultKernel.Castle.MicroKernel.IKernelInternal.Resolve(Type service, IDictionary arguments, IReleasePolicy policy)&#xD;   at Castle.Facilities.TypedFactory.TypedFactoryComponentResolver.Resolve(IKernelInternal kernel, IReleasePolicy scope)&#xD;   at Castle.Facilities.TypedFactory.Internal.TypedFactoryInterceptor.Resolve(IInvocation invocation)&#xD;   at Castle.Facilities.TypedFactory.Internal.TypedFactoryInterceptor.Intercept(IInvocation invocation)&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Castle.Proxies.IServiceFaultAssemblerFactoryProxy.GetServiceFaultAssembler(Exception e)&#xD;   at Grep.ManagementPlatform.Commons.ExceptionManagement.ServiceFaultAssemblerHelper.AssembleFault(IServiceFaultAssemblerFactory factory, Exception e) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\ExceptionManagement\\ServiceFaultAssemblerHelper.cs:line 14</StackTrace><Type>Castle.MicroKernel.ComponentNotFoundException</Type></InnerException><Message>Unable to obtain service fault from exception: System.ServiceModel.Security.SecurityAccessDeniedException: Provided credentials are not valid.&#xD;   at Grep.ManagementPlatform.Services.Business.Impl.ThrowOnFailureAuthenticationProvider.Throw() in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Business\\Impl\\ThrowOnFailureAuthenticationProvider.cs:line 42&#xD;   at Grep.ManagementPlatform.Services.Business.Impl.ThrowOnFailureAuthenticationProvider.ValidateCustomerCredentials(ServiceCredentialsDto credentials) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Business\\Impl\\ThrowOnFailureAuthenticationProvider.cs:line 30&#xD;   at Grep.ManagementPlatform.Services.Impl.ComponentServiceImpl.AuthenticateCustomerAccount(ServiceCredentialsDto credentials) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Services\\Impl\\ComponentServiceImpl.cs:line 104&#xD;   at Castle.Proxies.Invocations.IComponentService_AuthenticateCustomerAccount.InvokeMethodOnTarget()&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Data.Impl.NHibernate.Integration.NHTransactionIntegration.NHibernateTransactionInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Data.Impl.NHibernate\\Integration\\NHTransactionIntegration\\NHibernateTransactionInterceptor.cs:line 62&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Castle.Facilities.AutoTx.TransactionInterceptor.SynchronizedCase(IInvocation invocation, ITransaction transaction) in d:\\BuildAgent-03\\work\\9844bdf039249947\\src\\Castle.Facilities.AutoTx\\TransactionInterceptor.cs:line 179&#xD;   at Castle.Facilities.AutoTx.TransactionInterceptor.Castle.DynamicProxy.IInterceptor.Intercept(IInvocation invocation) in d:\\BuildAgent-03\\work\\9844bdf039249947\\src\\Castle.Facilities.AutoTx\\TransactionInterceptor.cs:line 119&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Commons.Integration.Interceptors.ServiceLoggingInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\Integration\\Interceptors\\ServiceLoggingInterceptor.cs:line 33&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Grep.ManagementPlatform.Commons.Integration.Interceptors.ServiceExceptionHandlingInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\Integration\\Interceptors\\ServiceExceptionHandlingInterceptor.cs:line 33</Message><StackTrace>   at Grep.ManagementPlatform.Commons.ExceptionManagement.ServiceFaultAssemblerHelper.AssembleFault(IServiceFaultAssemblerFactory factory, Exception e) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\ExceptionManagement\\ServiceFaultAssemblerHelper.cs:line 23&#xD;   at Grep.ManagementPlatform.Commons.Integration.Interceptors.ServiceExceptionHandlingInterceptor.Intercept(IInvocation invocation) in e:\\Work\\Grep\\ManagementPlatform\\src\\Grep.ManagementPlatform\\Grep.ManagementPlatform.Commons\\Integration\\Interceptors\\ServiceExceptionHandlingInterceptor.cs:line 64&#xD;   at Castle.DynamicProxy.AbstractInvocation.Proceed()&#xD;   at Castle.Proxies.IComponentServiceProxy.AuthenticateCustomerAccount(ServiceCredentialsDto credentials)&#xD;   at SyncInvokeAuthenticateCustomerAccount(Object , Object[] , Object[] )&#xD;   at System.ServiceModel.Dispatcher.SyncMethodInvoker.Invoke(Object instance, Object[] inputs, Object[]&amp; outputs)&#xD;   at System.ServiceModel.Dispatcher.DispatchOperationRuntime.InvokeBegin(MessageRpc&amp; rpc)&#xD;   at System.ServiceModel.Dispatcher.ImmutableDispatchRuntime.ProcessMessage5(MessageRpc&amp; rpc)&#xD;   at System.ServiceModel.Dispatcher.ImmutableDispatchRuntime.ProcessMessage31(MessageRpc&amp; rpc)&#xD;   at System.ServiceModel.Dispatcher.MessageRpc.Process(Boolean isOperationContextSet)</StackTrace><Type>System.InvalidOperationException</Type></ExceptionDetail></s:Detail></s:Fault></s:Body></s:Envelope>";
		//String s = "<html><head><title>404 Not Found</title></head><body><h1>ppppppppppppppppppppppppppppppppppppppppppppppppppppppphhhhhhhhhhhhhhhhhhhhhhhhhhNot Foundsddddddddddddddddxxxe</h1><hr></body></html>";
		//String s = "<html><head><title>404 Not Found</title></head><body><h1>Not Found</h1><p>The requested URL /ciao was not found on this server.</p><hr><address>Apache/2.2.22 (Debian) Server at 160.80.10.11 Port 80</address></body></html>";

		//String appoggio = "HTTP/1.1 200 OK\r\nContent-Length: " + s.length() + "\r\nContent-Type: text/html; charset=utf-8\r\nServer: Microsoft-HTTPAPI/2.0\r\n\r\n"+s;
		//payloadResponce = appoggio.getBytes();

		//length = appoggio.length();
		//Log.d("sendToApp", "LENGHT :" + length);
		//Log.d("paket P","payload:" + new String(receiveData));

		int lengthPacket = payloadLength + pktInfo.ip4Header.headerLength;

		lengthPacket += Packet.UDP_HEADER_SIZE;		

		synchronized(this){
			try {
				Log.d("sendToApp", "OLD1:" + pktInfo.toString() + "\n" + new String(pktInfo.backingBuffer.array()));
				pktInfo.backingBuffer.position(0);

				Packet sendToAppPkt = new Packet(pktInfo.backingBuffer);
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
				Log.d("sendToApp1", "bking:" + new String(bufferFromNetwork.array()));

			} catch (Exception e) {e.printStackTrace();}
		}


	}

}
