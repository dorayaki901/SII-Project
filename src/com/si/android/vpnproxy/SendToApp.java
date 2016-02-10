package com.si.android.vpnproxy;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import android.net.VpnService;
import android.util.Log;
import java.nio.channels.FileChannel;

public class SendToApp implements Runnable{

	ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue ;
	FileOutputStream out;
	String TAG = "sendToAPP";
	FileChannel vpnOutput = null;
	public SendToApp(FileOutputStream out,ConcurrentLinkedQueue<ByteBuffer> sentoToAppQueue) {
		this.sentoToAppQueue = sentoToAppQueue;
		this.out = out;
		this.vpnOutput = out.getChannel();
	}


	@Override
	public void run() {
		ByteBuffer buffToApp = null;
		while(true){
			try {
				while ((buffToApp = sentoToAppQueue.poll()) != null) {
					//buffToApp.position(0);
					//while(buffToApp.hasRemaining())
					//vpnOutput.write(buffToApp);
//					CustomLog.i("STAPP", new String(buffToApp.array()));
					out.write(buffToApp.array());
//					if(buffToApp.array().length>200){
//					out.write(buffToApp.array(),0,2000);
//					out.write(buffToApp.array(), 200, buffToApp.array().length - 200);
//					}
					buffToApp.position(0);
				}
				Thread.sleep(500);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}



}
