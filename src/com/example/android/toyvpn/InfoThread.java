package com.example.android.toyvpn;

import java.io.PipedOutputStream;

public class InfoThread {
	public Thread mThread;
	public PipedOutputStream mPipeOutputStream;
	
	public InfoThread(Thread mThread, PipedOutputStream mPipeOutputStream) {
		this.mThread = mThread;
		this.mPipeOutputStream = mPipeOutputStream;
	}

}
