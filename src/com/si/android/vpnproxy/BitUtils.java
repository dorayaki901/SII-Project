package com.si.android.vpnproxy;

import java.nio.ByteBuffer;

class BitUtils
{
    static short getUnsignedByte(byte value)
    {
        return (short)(value & 0xFF);
    }

    static int getUnsignedShort(short value)
    {
        return value & 0xFFFF;
    }

    static long getUnsignedInt(int value)
    {
        return value & 0xFFFFFFFFL;
    }
    
    static byte[] toBytes(int i)
    {
      byte[] result = new byte[4];

      result[0] = (byte) (i >> 24);
      result[1] = (byte) (i >> 16);
      result[2] = (byte) (i >> 8);
      result[3] = (byte) (i);

      return result;
    }
        
    static int getUnsignedInt(byte value){
    	return value & 0xFF;
    }
    
    static int toInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
   }
    
}