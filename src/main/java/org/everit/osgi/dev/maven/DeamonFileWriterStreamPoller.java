package org.everit.osgi.dev.maven;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DeamonFileWriterStreamPoller implements Closeable {

    private class PollerRunnable implements Runnable {

        @Override
        public void run() {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            try {
                while (!closed && ((line = bufferedReader.readLine()) != null)) {
                    fout.write(line.getBytes());
                    fout.write("\n".getBytes());
                }
                inputStream.close();
                closed = true;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    private boolean closed;

    private File file;

    private FileOutputStream fout;

    private InputStream inputStream;

    public DeamonFileWriterStreamPoller(final InputStream inputStream, final File file) {
        this.inputStream = inputStream;
        this.file = file;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            inputStream.close();
            fout.close();
        }

    }

    public void start() throws IOException {
        fout = new FileOutputStream(file);
        new Thread(new PollerRunnable()).start();
    }
}
