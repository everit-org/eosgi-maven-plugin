package org.everit.osgi.dev.maven.result;

/*
 * Copyright (c) 2011, Everit Kft.
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ServerSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives the test results from the OSGI containers sent by the
 * {@link org.everit.osgi.testing.maven.tinybundle.OsgiTestResponseActivator}.
 */
public class SocketTestResultReceiver implements Closeable {

    /**
     * Inner class that receives the data on new thread always.
     */
    protected class DataReceiver implements Runnable {
        /**
         * The socket that receives the data.
         */
        private Socket socket;

        /**
         * Constructor.
         * 
         * @param socket
         *            The socket that the data comes through.
         */
        public DataReceiver(final Socket socket) {
            this.socket = socket;
        }

        /**
         * Converting a string to an integer.
         * 
         * @param s
         *            The String that is converted to number.
         * @return The number representation or null if a {@link NumberFormatException} occurs.
         */
        protected Long convertStringToInteger(final String s) {
            Long result = null;
            try {
                result = Long.valueOf(s);
            } catch (NumberFormatException e) {
                LOGGER.warn("Could not read number value during processing test result from OSGI container: " + s);
            }
            return result;
        }

        @Override
        public void run() {
            InputStream inputStream = null;
            try {
                inputStream = socket.getInputStream();
                Properties resultProps = new Properties();
                resultProps.load(inputStream);
                TestResult testResult = new TestResult();
                String testContainerId = resultProps.getProperty("testContainerId");

                if (testContainerId == null) {
                    LOGGER.error("Response arrived from OSGI container without container id: " + resultProps.toString());
                    return;
                }

                testResult.setRunCount(convertStringToInteger(resultProps.getProperty("runCount")));
                testResult.setErrorCount(convertStringToInteger(resultProps.getProperty("errors")));
                testResult.setFailureCount(convertStringToInteger(resultProps.getProperty("failures")));
                testResult.setIgnoreCount(convertStringToInteger(resultProps.getProperty("skipped")));
                testResult.setRunTime(convertStringToInteger(resultProps.getProperty("runTime")));
                System.out.println("Putting testResult with container id: " + testContainerId);
                testResults.put(testContainerId, testResult);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                if (!socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Inner class that listens on a server socket on a new thread.
     */
    protected class ServerSocketListener implements Runnable {
        @Override
        public void run() {
            try {
                while (!serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    LOGGER.debug("Data receiving from OSGI test Container");
                    new Thread(new DataReceiver(socket)).start();
                }
            } catch (SocketException e) {
                if (serverSocket.isClosed()) {
                    LOGGER.debug("Socket closed");
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * The logger of this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketTestResultReceiver.class);

    /**
     * The server socket that receives the data.
     */
    private final ServerSocket serverSocket;

    /**
     * The received test results by the test container ids.
     */
    private Map<String, TestResult> testResults = new ConcurrentHashMap<String, TestResult>();

    /**
     * Constructor.
     * 
     * @param inetAddress
     *            The address that the serversocket is opened on. Normally this is the default localhost.
     * @param port
     *            The port on which the serverSocket should lisen. If null a random port is provided.
     * @throws IOException
     *             if the server socket cannot be opened.
     */
    public SocketTestResultReceiver(final InetAddress inetAddress, final int port) throws IOException {
        InetAddress tmpAddress = inetAddress;
        if (tmpAddress == null) {
            tmpAddress = InetAddress.getLocalHost();
        }
        serverSocket = ServerSocketFactory.getDefault().createServerSocket(port, 10, tmpAddress);
        new Thread(new ServerSocketListener()).start();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();

    }

    @Override
    protected void finalize() throws Throwable {
        if (!serverSocket.isClosed()) {
            LOGGER.warn("YOU (THE PROGRAMMER) FORGOT TO CALL THE CLOSE FUNCTION ON "
                    + SocketTestResultReceiver.class.getName());
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.finalize();
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Getting back a test result that may arrived from a test container.
     * 
     * @param containerId
     *            The id of the test container where we expect the result from.
     * @return The test results.
     */
    public TestResult getTestResult(final String containerId) {
        return testResults.get(containerId);
    }
}
