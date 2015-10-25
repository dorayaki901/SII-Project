package com.example.android.toyvpn;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class ServerLog extends Service implements Runnable {
	
	private Thread mThread;
	
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		Log.i("ServerLog", "Server Starting");
	}	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("ServerLog", "Server Starting");
		
		mThread = new Thread(this, "ServerThread");
  	  	//start the service
  	  	mThread.start();
		//return super.onStartCommand(intent, flags, startId);
		return START_STICKY;  // Restart the service if got killed
	}



	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized void run() {
		Log.i("ServerLog", "Server Starting");
		while(true){
			;
			
		}
	}

}
