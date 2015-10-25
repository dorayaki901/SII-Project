/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.toyvpn;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.net.VpnService.Builder;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Enumeration;

import org.apache.http.conn.util.InetAddressUtils;

public class ToyVpnService extends VpnService implements Handler.Callback, Runnable {
    private static final String TAG = "ToyVpnService";

    private String mServerAddress;
    private String mServerPort;
    private byte[] mSharedSecret;
    private PendingIntent mConfigureIntent;

    private Handler mHandler;
    private Thread mThread;

    private ParcelFileDescriptor mInterface;
    private String mParameters;
    public byte[] InBuff = new byte[200]; 
	  //a. Configure a builder for the interface.
	  Builder builder = new Builder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	// The handler is only used to show messages.
    	Log.i("ciao", "onCreate");   
    	mThread = new Thread(this, "ToyVpnThread");
  	  	//start the service
  	  	mThread.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mThread != null) {
            mThread.interrupt();
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        if (message != null) {
            Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    public synchronized void run() {
    	try {
    		Log.i("ciao", "onCreate"); 
	  	  	  //a. Configure the TUN and get the interface.
	  	  	  mInterface = builder.setSession("MyVPNService")
	  	  	  	.addAddress(getLocalIpAddress(), 24)
	  	  	  	.addDnsServer("8.8.8.8")
	  	  	  	.addRoute("0.0.0.0", 0).establish();
	  	  	  //b. Packets to be sent are queued in this input stream.
	  	  	  FileInputStream in = new FileInputStream(
	  	  	  		mInterface.getFileDescriptor());
	  	  	  //b. Packets received need to be written to this output stream.
	  	  	  FileOutputStream out = new FileOutputStream(
	  	  	  		mInterface.getFileDescriptor());
	                  //c. The UDP channel can be used to pass/get ip package to/from server
	  	  	  DatagramChannel tunnel = DatagramChannel.open();
	  	  	  // Connect to the server, localhost is used for demonstration only.
	  	  	  tunnel.connect(new InetSocketAddress(getLocalIpAddress(),80));
	  	  	  //d. Protect this socket, so package send by it will not be feedback to the vpn service.
	  	  	  protect(tunnel.socket());
	  	  	  
	  	  	 ByteBuffer packet = ByteBuffer.allocate(32767);
	  	  	 int timer = 0;
	  	  	
	  	  	  //e. Use a loop to pass packets.
	  	   while (true) {
               // Assume that we did not make any progress in this iteration.
               boolean idle = true;
               // Read the outgoing packet from the input stream.
               int length = in.read(packet.array());
               Log.i("LOG", "" + length);
               if (length > 0) {
                   // Write the outgoing packet to the tunnel.
                   packet.limit(length);
                   tunnel.write(packet);
                   
                   packet.clear();
                   // There might be more outgoing packets.
                   idle = false;
                   // If we were receiving, switch to sending.
                   if (timer < 1) {
                       timer = 1;
                   }
               }
               // Read the incoming packet from the tunnel.
               length = tunnel.read(packet);
               if (length > 0) {
            	   Log.i("0", "asdadas" );
                   // Ignore control messages, which start with zero.
                   if (packet.get(0) != 0) {
                       // Write the incoming packet to the output stream.
                	   Log.i("1", "" + length);
                       out.write(packet.array(), 0, length);
                       Log.i("2", "" + length);
                   }
                   packet.clear();
                   // There might be more incoming packets.
                   idle = false;
                   // If we were sending, switch to receiving.
                   if (timer > 0) {
                       timer = 0;
                   }
               }
               // If we are idle or waiting for the network, sleep for a
               // fraction of time to avoid busy looping.
               if (idle) {
                   Thread.sleep(100);
                   // Increase the timer. This is inaccurate but good enough,
                   // since everything is operated in non-blocking mode.
                   timer += (timer > 0) ? 100 : -100;
                   // We are receiving for a long time but not sending.
                   if (timer < -15000) {
                       // Send empty control messages.
                       packet.put((byte) 0).limit(1);
                       for (int i = 0; i < 3; ++i) {
                           packet.position(0);
                           tunnel.write(packet);
                       }
                       packet.clear();
                       // Switch to sending.
                       timer = 1;
                   }
                   // We are sending for a long time but not receiving.
                   if (timer > 20000) {
                       throw new IllegalStateException("Timed out");
                   }
               }
           }
	  	  	} catch (Exception e) {
	  	  	  // Catch any exception
	  	  	  e.printStackTrace();
	  	  	} finally {
	  	  	  try {
	  	  	  	if (mInterface != null) {
	  	  	  		mInterface.close();
	  	  	  		mInterface = null;
	  	  	  	}
	  	  	  } catch (Exception e) {

	  	  	  }
	  	  	}
	  	  }

    private boolean run(InetSocketAddress server) throws Exception {
        DatagramChannel tunnel = null;
        boolean connected = false;
        try {
            // Create a DatagramChannel as the VPN tunnel.
            tunnel = DatagramChannel.open();

            // Protect the tunnel before connecting to avoid loopback.
            if (!protect(tunnel.socket())) {
                throw new IllegalStateException("Cannot protect the tunnel");
            }

            // Connect to the server.
            tunnel.connect(server);

            // For simplicity, we use the same thread for both reading and
            // writing. Here we put the tunnel into non-blocking mode.
            tunnel.configureBlocking(false);

            // Authenticate and configure the virtual network interface.
            handshake(tunnel);

            // Now we are connected. Set the flag and show the message.
            connected = true;
            mHandler.sendEmptyMessage(R.string.connected);

            // Packets to be sent are queued in this input stream.
            FileInputStream in = new FileInputStream(mInterface.getFileDescriptor());

            // Packets received need to be written to this output stream.
            FileOutputStream out = new FileOutputStream(mInterface.getFileDescriptor());

            // Allocate the buffer for a single packet.
            ByteBuffer packet = ByteBuffer.allocate(32767);

            // We use a timer to determine the status of the tunnel. It
            // works on both sides. A positive value means sending, and
            // any other means receiving. We start with receiving.
            int timer = 0;

            // We keep forwarding packets till something goes wrong.
            while (true) {
                // Assume that we did not make any progress in this iteration.
                boolean idle = true;

                // Read the outgoing packet from the input stream.
                int length = in.read(packet.array());
                if (length > 0) {
                    // Write the outgoing packet to the tunnel.
                    packet.limit(length);
                    tunnel.write(packet);
                    packet.clear();

                    // There might be more outgoing packets.
                    idle = false;

                    // If we were receiving, switch to sending.
                    if (timer < 1) {
                        timer = 1;
                    }
                }

                // Read the incoming packet from the tunnel.
                length = tunnel.read(packet);
                if (length > 0) {
                    // Ignore control messages, which start with zero.
                    if (packet.get(0) != 0) {
                        // Write the incoming packet to the output stream.
                        out.write(packet.array(), 0, length);
                    }
                    packet.clear();

                    // There might be more incoming packets.
                    idle = false;

                    // If we were sending, switch to receiving.
                    if (timer > 0) {
                        timer = 0;
                    }
                }

                // If we are idle or waiting for the network, sleep for a
                // fraction of time to avoid busy looping.
                if (idle) {
                    Thread.sleep(100);

                    // Increase the timer. This is inaccurate but good enough,
                    // since everything is operated in non-blocking mode.
                    timer += (timer > 0) ? 100 : -100;

                    // We are receiving for a long time but not sending.
                    if (timer < -15000) {
                        // Send empty control messages.
                        packet.put((byte) 0).limit(1);
                        for (int i = 0; i < 3; ++i) {
                            packet.position(0);
                            tunnel.write(packet);
                        }
                        packet.clear();

                        // Switch to sending.
                        timer = 1;
                    }

                    // We are sending for a long time but not receiving.
                    if (timer > 20000) {
                        throw new IllegalStateException("Timed out");
                    }
                }
            }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Got " + e.toString());
        } finally {
            try {
                tunnel.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return connected;
    }

    private void handshake(DatagramChannel tunnel) throws Exception {
        // To build a secured tunnel, we should perform mutual authentication
        // and exchange session keys for encryption. To keep things simple in
        // this demo, we just send the shared secret in plaintext and wait
        // for the server to send the parameters.

        // Allocate the buffer for handshaking.
        ByteBuffer packet = ByteBuffer.allocate(1024);

        // Control messages always start with zero.
        packet.put((byte) 0).put(mSharedSecret).flip();

        // Send the secret several times in case of packet loss.
        for (int i = 0; i < 3; ++i) {
            packet.position(0);
            tunnel.write(packet);
        }
        packet.clear();

        // Wait for the parameters within a limited time.
        for (int i = 0; i < 50; ++i) {
            Thread.sleep(100);

            // Normally we should not receive random packets.
            int length = tunnel.read(packet);
            if (length > 0 && packet.get(0) == 0) {
                configure(new String(packet.array(), 1, length - 1).trim());
                return;
            }
        }
        throw new IllegalStateException("Timed out");
    }

    private void configure(String parameters) throws Exception {
        // If the old interface has exactly the same parameters, use it!
        if (mInterface != null && parameters.equals(mParameters)) {
            Log.i(TAG, "Using the previous interface");
            return;
        }

        // Configure a builder while parsing the parameters.
        Builder builder = new Builder();
        for (String parameter : parameters.split(" ")) {
            String[] fields = parameter.split(",");
            try {
                switch (fields[0].charAt(0)) {
                    case 'm':
                        builder.setMtu(Short.parseShort(fields[1]));
                        break;
                    case 'a':
                        builder.addAddress(fields[1], Integer.parseInt(fields[2]));
                        break;
                    case 'r':
                        builder.addRoute(fields[1], Integer.parseInt(fields[2]));
                        break;
                    case 'd':
                        builder.addDnsServer(fields[1]);
                        break;
                    case 's':
                        builder.addSearchDomain(fields[1]);
                        break;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Bad parameter: " + parameter);
            }
        }

        // Close the old interface since the parameters have been changed.
        try {
            mInterface.close();
        } catch (Exception e) {
            // ignore
        }

        // Create a new interface using the builder and save the parameters.
        mInterface = builder.setSession(mServerAddress)
                .setConfigureIntent(mConfigureIntent)
                .establish();
        mParameters = parameters;
        Log.i(TAG, "New interface: " + parameters);
    }
    
    
    public String getLocalIpAddress() {
        String ipv4;
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    System.out.println("ip1--:" + inetAddress);
                    System.out.println("ip2--:" + inetAddress.getHostAddress());

                    // for getting IPV4 format
                    if ( !inetAddress.isLoopbackAddress() && InetAddressUtils.isIPv4Address(ipv4 = inetAddress.getHostAddress())) {

                        String ip = inetAddress.getHostAddress().toString();
                        System.out.println("ip---::" + ip);
                        // return inetAddress.getHostAddress().toString();
                        return ipv4;
                    }
                }
            }
        }
        catch (Exception ex) {
            Log.e("IP Address", ex.toString());
        }
        return null;
    }
    
}
