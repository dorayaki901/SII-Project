package com.example.android.toyvpn;

import java.nio.ByteBuffer;

import android.util.Log;

public class PacketInfo {
	private static final String TAG = "PacketInfo";
	private int version;
	private int headerLength;
	private int totalLengh;
	private int protocol;
	private boolean  isTCP;
	private boolean  isUDP;
	private int port;
	private String destIP;
	private String sourceIP;
	
	//private ByteBuffer datagram;

	public void debugPacket(ByteBuffer packet)
    {
 
        int buffer = packet.get(); // take the first byte (version + H.length)
        version = buffer >> 4; // Shift to right for read the header first 4 bit (msb)
        headerLength = buffer & 0x0F; // take last four lsb bit
        headerLength *= 4;

        buffer = packet.get();      //DSCP + EN
        buffer = packet.getChar();  //Total Length
        
        totalLengh=buffer;
        
        
        buffer = packet.getChar();  //Identification
        buffer = packet.getChar();  //Flags + Fragment Offset
        buffer = packet.get();      //Time to Live
        buffer = packet.get();      //Protocol
        protocol = buffer;


        buffer = packet.getChar();  //Header checksum
        
        sourceIP  = "";
        buffer = packet.get() & 0xFF;  //Source IP 1st Octet
        sourceIP += buffer;
        sourceIP += ".";

        buffer = packet.get() & 0xFF;  //Source IP 2nd Octet
        sourceIP += buffer;
        sourceIP += ".";

        buffer = packet.get() & 0xFF;  //Source IP 3rd Octet
        sourceIP += buffer;
        sourceIP += ".";

        buffer = packet.get() & 0xFF;  //Source IP 4th Octet
        sourceIP += buffer;
        
        destIP  = "";
        buffer = packet.get() & 0xFF;  //Destination IP 1st Octet
        destIP += buffer;
        destIP += ".";

        buffer = packet.get() & 0xFF;  //Destination IP 2nd Octet
        destIP += buffer;
        destIP += ".";

        buffer = packet.get() & 0xFF;  //Destination IP 3rd Octet
        destIP += buffer;
        destIP += ".";

        buffer = packet.get() & 0xFF;  //Destination IP 4th Octet
        destIP += buffer;
        

        buffer = packet.get();  
        buffer = packet.get();  
        packet.position(0);
        port = packet.get(packet.array(),headerLength + 2, 2).getInt(); 
        
    }
	
	public void printPacketInfo(){
//		if(destIP.equals("1.0.0.1")){
		 Log.i(TAG, "IP Version:"+version);
	     Log.i(TAG, "Header Length:" + headerLength);
	     Log.i(TAG, "Total Length:" + totalLengh);
	     Log.i(TAG, "Protocol:" + protocol);
	     Log.i(TAG, "Source IP:" + sourceIP);
	     Log.i(TAG, "Destination IP:" + destIP);
	     Log.i(TAG, "Destination Port:"+ port);
//		}
	     
			if(destIP.equals("1.0.0.1")){
			 Log.i("ciao", "IP Version:"+version);
		     Log.i("ciao", "Header Length:" + headerLength);
		     Log.i("ciao", "Total Length:" + totalLengh);
		     Log.i("ciao", "Protocol:" + protocol);
		     Log.i("ciao", "Source IP:" + sourceIP);
		     Log.i("ciao", "Destination IP:" + destIP);
		     Log.i("ciao", "Destination Port:"+ port);
			}
	
	}
	
	public int getVersion() {
		return version;
	}

	public int getHeaderLength() {
		return headerLength;
	}

	public int getTotalLengh() {
		return totalLengh;
	}

	public int getProtocol() {
		return protocol;
	}

	public String getDestIP() {
		return destIP;
	}

	public String getSourceIP() {
		return sourceIP;
	}
	
	   private static class BitUtils
	    {
	        private static short getUnsignedByte(byte value)
	        {
	            return (short)(value & 0xFF);
	        }

	        private static int getUnsignedShort(short value)
	        {
	            return value & 0xFFFF;
	        }

	        private static long getUnsignedInt(int value)
	        {
	            return value & 0xFFFFFFFFL;
	        }
	    }
}
