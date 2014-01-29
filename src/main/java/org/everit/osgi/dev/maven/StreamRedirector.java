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
