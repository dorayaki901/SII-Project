package com.si.android.vpnproxy;

import java.net.InetAddress;

import android.util.Log;

public class IdentifierKeyThread {
	public InetAddress destinationIP;
	public int destinationPort;
	public int sourcePort;
	public boolean isTCP;


	public void setDestinationIP(InetAddress destinationIP) {
		this.destinationIP = destinationIP;
	}
	public void setDestinationPort(int destinationPort) {
		this.destinationPort = destinationPort;
	}
	public void setSourcePort(int sourcePort) {
		this.sourcePort = sourcePort;
	}
	public void setIsTCP(boolean isTCP) {
		this.isTCP = isTCP;
	}


	@Override
	public boolean equals(Object o) {
		IdentifierKeyThread app = (IdentifierKeyThread) o;	

		if (app.destinationIP.getHostAddress().equals(destinationIP.getHostAddress()) 
				&& app.destinationPort==destinationPort 
				&& app.sourcePort==sourcePort
				&& app.isTCP == isTCP
				){
			return true;
		}
		return false;
	}

	@Override
	public int hashCode() {
		String hashString = destinationIP.getHostAddress()+destinationPort+sourcePort+isTCP;
		return hashString.hashCode();
	}

	public void set(Packet appPacket) {

		this.destinationIP = appPacket.ip4Header.destinationAddress;

		if (appPacket.isTCP()){
			this.destinationPort = appPacket.tcpHeader.destinationPort;
			this.sourcePort = appPacket.tcpHeader.sourcePort;
			this.isTCP = true;

		}else if(appPacket.isUDP()){
			this.destinationPort = appPacket.udpHeader.destinationPort;
			this.sourcePort = appPacket.udpHeader.sourcePort;
			this.isTCP = false;
		}

		//TODO else ??????? se è un altro tipo cm si fa? (può essere mi dava null pointer exception)

	}
}
