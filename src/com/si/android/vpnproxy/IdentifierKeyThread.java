package com.si.android.vpnproxy;

public class IdentifierKeyThread {

	public static Integer hashCode(Packet packet) {
		String hashString = packet.ip4Header.destinationAddress.getHostAddress()+ 
							((packet.isTCP()) ? 
									(packet.tcpHeader.destinationPort+packet.tcpHeader.sourcePort) :
									(packet.udpHeader.destinationPort+packet.udpHeader.sourcePort)) + packet.isTCP();
		return Integer.valueOf(hashString.hashCode());
	}	
	
}
