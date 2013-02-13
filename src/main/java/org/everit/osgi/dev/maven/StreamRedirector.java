package org.everit.osgi.dev.maven;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamRedirector implements Runnable {

    private InputStream inputStream;

    private OutputStream outputStream;
    
    private boolean closeInputStreamOnStop;
    
    private boolean closeOutputStreamOnStop;

    private boolean stopped = false;

    public StreamRedirector(InputStream inputStream, OutputStream outputStream, boolean closeInputStreamOnStop,
            boolean closeOutputStreamOnStop) {
        super();
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.closeInputStreamOnStop = closeInputStreamOnStop;
        this.closeOutputStreamOnStop = closeOutputStreamOnStop;
    }

    public void stop() {
        stopped = true;
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
}
