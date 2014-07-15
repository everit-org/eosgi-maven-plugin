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
package org.everit.osgi.dev.maven;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.plugin.logging.Log;

/**
 * Redirects the input of an InputStream to one or more output streams.
 */
public class DaemonStreamRedirector implements Closeable {

    private class PollerRunnable implements Runnable {

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int r;
            try {
                r = inputStream.read(buffer);
            } catch (IOException e) {
                log.error("Error reading from input stream. Stopping redirection.", e);
                return;
            }

            while (!closed.get() && r > -1) {
                Iterator<OutputStream> iterator = outputStreamList.iterator();
                while (iterator.hasNext()) {
                    OutputStream out = iterator.next();
                    try {
                        out.write(buffer, 0, r);
                    } catch (IOException e) {
                        log.error("Error writing to outputStream. Ignoring it in the future.", e);
                        iterator.remove();
                    }
                }

                try {
                    r = inputStream.read(buffer);
                } catch (IOException e) {
                    log.error("Error reading from input stream. Stopping redirection.", e);
                    return;
                }
            }
        }
    }

    private AtomicBoolean closed = new AtomicBoolean(true);

    private List<OutputStream> outputStreamList;

    private InputStream inputStream;

    private Log log;

    public DaemonStreamRedirector(final InputStream inputStream, final OutputStream[] outputStreams, final Log log) {
        this.inputStream = inputStream;
        this.log = log;
        this.outputStreamList = new ArrayList<OutputStream>(Arrays.asList(outputStreams));
    }

    /**
     * Closes the backgroundThread, the input stream and all of the output streams.
     */
    @Override
    public void close() throws IOException {
        if (!closed.get()) {
            closed.set(true);
            IOException mainException = null;
            try {
                inputStream.close();
            } catch (IOException e) {
                mainException = e;
            }

            for (OutputStream outputStream : outputStreamList) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    if (mainException == null) {
                        mainException = e;
                    } else {
                        mainException.addSuppressed(e);
                    }
                }
            }
            if (mainException != null) {
                throw mainException;
            }
        }
    }

    public void start() throws IOException {
        closed.set(false);
        new Thread(new PollerRunnable()).start();
    }
}
