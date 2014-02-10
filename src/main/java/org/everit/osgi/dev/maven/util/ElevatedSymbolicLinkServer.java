package org.everit.osgi.dev.maven.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

public class ElevatedSymbolicLinkServer {

    private class ShutdownHook extends Thread {
        @Override
        public void run() {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static ServerSocket serverSocket;

    public static void main(final String[] args) {
        int port = Integer.valueOf(args[0]);
        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            serverSocket = new ServerSocket(port, 1, localAddress);
            Socket socket = serverSocket.accept();
            InputStream in = socket.getInputStream();
            InputStreamReader reader = new InputStreamReader(in, Charset.defaultCharset());
            BufferedReader br = new BufferedReader(reader);
            String line = br.readLine();
            while (line != null) {
                line = br.readLine();
            }
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not get localhost address");
        } catch (IOException e) {
            throw new RuntimeException("Could not bind to local address on port " + port, e);
        }
    }
}
