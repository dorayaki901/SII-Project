package com.si.android.vpnproxy;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class InfoThread {
	public Thread mThread;
	public PipedOutputStream mPipeOutputStream;

	
	public InfoThread(Thread mThread, PipedOutputStream mPipeOutputStream) {
		this.mThread = mThread;
		this.mPipeOutputStream = mPipeOutputStream;
	}

}
