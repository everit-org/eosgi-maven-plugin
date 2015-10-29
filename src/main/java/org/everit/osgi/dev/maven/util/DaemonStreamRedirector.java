/*
 * Copyright (C) 2011 Everit Kft. (http://everit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.osgi.dev.maven.util;

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

  /**
   * The runnable of the thread that does the copy between the streams.
   */
  private class PollerRunnable implements Runnable {

    private static final int BUFFER_SIZE = 1024;

    @Override
    public void run() {
      byte[] buffer = new byte[BUFFER_SIZE];
      int r;
      try {
        r = inputStream.read(buffer);
      } catch (IOException e) {
        log.error("Error reading from input stream. Stopping redirection.", e);
        return;
      }

      while (!closed.get() && (r > -1)) {
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

  private InputStream inputStream;

  private Log log;

  private List<OutputStream> outputStreamList;

  /**
   * Constructor.
   *
   * @param inputStream
   *          The inputstream where data will be read from.
   * @param outputStreams
   *          The outputstream where the data will be written to.
   * @param log
   *          A logger that is used to log unexpected behavior.
   */
  public DaemonStreamRedirector(final InputStream inputStream, final OutputStream[] outputStreams,
      final Log log) {
    this.inputStream = inputStream;
    this.log = log;
    outputStreamList = new ArrayList<OutputStream>(Arrays.asList(outputStreams));
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
