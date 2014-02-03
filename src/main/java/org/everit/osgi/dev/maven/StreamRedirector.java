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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamRedirector implements Runnable {

    private boolean closeInputStreamOnStop;

    private boolean closeOutputStreamOnStop;

    private InputStream inputStream;

    private OutputStream outputStream;

    private boolean stopped = false;

    public StreamRedirector(final InputStream inputStream, final OutputStream outputStream,
            final boolean closeInputStreamOnStop, final boolean closeOutputStreamOnStop) {
        super();
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.closeInputStreamOnStop = closeInputStreamOnStop;
        this.closeOutputStreamOnStop = closeOutputStreamOnStop;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1000];
        while (!stopped) {
            int read;
            try {
                read = inputStream.read(buffer);
                while (read > 0) {
                    outputStream.write(buffer, 0, read);
                    read = inputStream.read(buffer);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if (closeInputStreamOnStop) {
            try {
                inputStream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if (closeOutputStreamOnStop) {
            try {
                outputStream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        stopped = true;
    }
}
