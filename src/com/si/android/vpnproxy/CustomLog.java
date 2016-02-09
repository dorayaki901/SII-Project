package com.si.android.vpnproxy;

import android.util.Log;

public class CustomLog {
	
	static void i(String tag,String msg){
		msg=msg.replaceAll("\n", " - ");
		Log.i(tag, msg);
	}

}
