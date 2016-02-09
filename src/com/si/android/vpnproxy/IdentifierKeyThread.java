package com.si.android.vpnproxy;

public class IdentifierKeyThread {

	public static Integer hashCode(Packet packet) {

		String hashString = packet.ip4Header.destinationAddress.getHostAddress()+"";
		if (packet.isTCP())	
			hashString +=packet.tcpHeader.destinationPort+""+packet.tcpHeader.sourcePort;
		else
			hashString +=packet.udpHeader.destinationPort+""+packet.udpHeader.sourcePort; 
		hashString +=""+ packet.isTCP();
		
		
		return Integer.valueOf(hashString.hashCode());
		
	}	
	
}
