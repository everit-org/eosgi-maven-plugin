/**
 * This file is part of Everit - Maven OSGi plugin.
 *
 * Everit - Maven OSGi plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Maven OSGi plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Maven OSGi plugin.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.dev.maven.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This class is intended to internal usage only!
 * 
 * The purpose of this class to make it possible to create symbolic links in elevated mode on windows systems. The main
 * class takes one argument that is a port where a server will listen. The socket server accepts two commands:
 * <ul>
 * <li>createSymbolicLink sourceURI targetURI</li>
 * <li>stop</li>
 * </ul>
 */
public class ElevatedSymbolicLinkServer {

    public static final String COMMAND_CREATE_SYMBOLIC_LINK = "createSymbolicLink";

    private static final String COMMAND_STOP = "stop";

    private static ServerSocket serverSocket;

    private static void createTestSymbolicLink() throws IOException {
        File tempFile = null;
        File tmpSymbolicLinkFile = null;
        try {
            tempFile = File.createTempFile("eosgi-", "-testFile");
            tmpSymbolicLinkFile = File.createTempFile("eosgi-", "-testSymbolicLink");
            tmpSymbolicLinkFile.delete();

            Path tmpSymbolicLinkPath = tmpSymbolicLinkFile.toPath();
            Files.createSymbolicLink(tmpSymbolicLinkPath, tempFile.toPath());

            Path originalPath = Files.readSymbolicLink(tmpSymbolicLinkPath);
            File originalFile = originalPath.toFile();
            if (!originalFile.equals(tempFile)) {
                throw new IOException("It seems that the system cannot handle symbolic links. "
                        + "Error during checking test symbolic links. " + tempFile + " and " + originalFile
                        + " should be the same.");
            }
        } finally {
            if (tmpSymbolicLinkFile != null) {
                tmpSymbolicLinkFile.delete();
            }
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    public static void main(final String[] args) {
        try {
            createTestSymbolicLink();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int port = Integer.valueOf(args[0]);
        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            System.out.println("Opening symbolicLinkServer at " + localAddress.toString() + " on port " + port);
            serverSocket = new ServerSocket(port, 1, localAddress);
            Socket socket = serverSocket.accept();
            InputStream in = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            InputStreamReader reader = new InputStreamReader(in, Charset.defaultCharset());
            BufferedReader br = new BufferedReader(reader);
            String line = br.readLine();
            boolean stopped = false;
            while (!stopped && line != null) {
                if (!line.equals(COMMAND_STOP)) {
                    processLine(line, outputStream);
                    line = br.readLine();
                } else {
                    System.out.println("Caughed stop command. Stopping server.");
                    stopped = true;
                }
            }
        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not get localhost address");
        } catch (IOException e) {
            throw new RuntimeException("Could not bind to local address on port " + port, e);
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void processLine(final String line, final OutputStream out) {
        if (line.startsWith(COMMAND_CREATE_SYMBOLIC_LINK)) {

            String[] fileURIs = line.substring(COMMAND_CREATE_SYMBOLIC_LINK.length() + 1).split(" ");
            String sourceURIString = fileURIs[0];
            String targetURIString = fileURIs[1];
            try {
                URI sourceURI = new URI(sourceURIString);
                URI targetURI = new URI(targetURIString);
                File sourceFile = new File(sourceURI);
                File targetFile = new File(targetURI);

                System.out.println("Creating symbolic link " + targetFile.getAbsolutePath() + " that points to "
                        + sourceFile.getAbsolutePath());
                Files.createSymbolicLink(targetFile.toPath(), sourceFile.toPath());
                if (sourceFile.canExecute()) {
                    targetFile.setExecutable(true);
                } else {
                    targetFile.setExecutable(false);
                }
                out.write("ok\n".getBytes(Charset.defaultCharset()));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
