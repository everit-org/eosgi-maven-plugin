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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Generated;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.everit.osgi.dev.maven.jaxb.dist.definition.CopyModeType;
import org.rzo.yajsw.os.OperatingSystem;
import org.rzo.yajsw.os.ms.win.w32.WindowsXPProcess;
import org.rzo.yajsw.os.ms.win.w32.WindowsXPProcessManager;

/**
 * This class is not thread-safe. It should be used within one thread only.
 */
@Generated("asSymbolicLinkWillBeDeprecatedIgnoringCheckstyle")
public class FileManager implements AutoCloseable {

  /**
   * Closes the elevated symbolic link socket.
   */
  private class ShutdownHook extends Thread {
    @Override
    public void run() {
      shutdownHook = null;
      if (symbolicLinkServerSocket != null) {
        try {
          close();
        } catch (IOException e) {
          log.error("Error during closing FileManager", e);
        }
      }
    }
  }

  private static final int GROUP_EXECUTE_BITMASK;

  private static final int GROUP_READ_BITMASK;

  private static final int GROUP_WRITE_BITMASK;

  private static final int OTHERS_EXECUTE_BITMASK;

  private static final int OTHERS_READ_BITMASK;

  private static final int OTHERS_WRITE_BITMASK;

  private static final int OWNER_EXECUTE_BITMASK;

  private static final int OWNER_READ_BITMASK;

  private static final int OWNER_WRITE_BITMASK;

  static {
    final int octalDigitNum = 3;
    final int executeOctal = 1;
    OTHERS_EXECUTE_BITMASK = executeOctal;
    GROUP_EXECUTE_BITMASK = OTHERS_EXECUTE_BITMASK << octalDigitNum;
    OWNER_EXECUTE_BITMASK = GROUP_EXECUTE_BITMASK << octalDigitNum;

    final int writeOctal = 2;

    OTHERS_WRITE_BITMASK = writeOctal;
    GROUP_WRITE_BITMASK = OTHERS_WRITE_BITMASK << octalDigitNum;
    OWNER_WRITE_BITMASK = GROUP_WRITE_BITMASK << octalDigitNum;

    final int readOctal = 4;

    OTHERS_READ_BITMASK = readOctal;
    GROUP_READ_BITMASK = OTHERS_READ_BITMASK << octalDigitNum;
    OWNER_READ_BITMASK = GROUP_READ_BITMASK << octalDigitNum;

  }

  private static void setPermissionsOnFile(final File file,
      final ZipArchiveEntry entry) throws IOException {
    if (entry.getPlatform() == ZipArchiveEntry.PLATFORM_FAT) {
      return;
    }
    int unixPermissions = entry.getUnixMode();

    Set<PosixFilePermission> perms = new HashSet<>();

    if ((unixPermissions & OWNER_EXECUTE_BITMASK) > 0) {
      perms.add(PosixFilePermission.OWNER_EXECUTE);
    }

    if ((unixPermissions & GROUP_EXECUTE_BITMASK) > 0) {
      perms.add(PosixFilePermission.GROUP_EXECUTE);
    }

    if ((unixPermissions & OTHERS_EXECUTE_BITMASK) > 0) {
      perms.add(PosixFilePermission.OTHERS_EXECUTE);
    }

    if ((unixPermissions & OWNER_READ_BITMASK) > 0) {
      perms.add(PosixFilePermission.OWNER_READ);
    }

    if ((unixPermissions & GROUP_READ_BITMASK) > 0) {
      perms.add(PosixFilePermission.GROUP_READ);
    }

    if ((unixPermissions & OTHERS_READ_BITMASK) > 0) {
      perms.add(PosixFilePermission.OTHERS_READ);
    }

    if ((unixPermissions & OWNER_WRITE_BITMASK) > 0) {
      perms.add(PosixFilePermission.OWNER_WRITE);
    }

    if ((unixPermissions & GROUP_WRITE_BITMASK) > 0) {
      perms.add(PosixFilePermission.GROUP_WRITE);
    }

    if ((unixPermissions & OTHERS_WRITE_BITMASK) > 0) {
      perms.add(PosixFilePermission.OTHERS_WRITE);
    }

    Path path = file.toPath();
    if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      Files.setPosixFilePermissions(path, perms);
    } else {
      setPermissionsOnFileInNonPosixSystem(file, perms);
    }
  }

  private static void setPermissionsOnFileInNonPosixSystem(final File file,
      final Set<PosixFilePermission> perms) {

    if (perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
      file.setExecutable(true, !perms.contains(PosixFilePermission.OTHERS_EXECUTE));
    }

    if (perms.contains(PosixFilePermission.OWNER_READ)) {
      file.setReadable(true, !perms.contains(PosixFilePermission.OTHERS_READ));
    }

    if (perms.contains(PosixFilePermission.OWNER_WRITE)) {
      file.setWritable(true, !perms.contains(PosixFilePermission.OTHERS_WRITE));
    }

  }

  private final Log log;

  private ShutdownHook shutdownHook = null;

  private Socket symbolicLinkServerSocket = null;

  public FileManager(final Log log) {
    this.log = log;
  }

  /**
   * In case an elevated service was started, it will be stopped by calling this function.
   *
   * @throws IOException
   *           if there is a problem during writing to the symbolicLink server socket.
   */
  @Override
  public void close() throws IOException {
    if (shutdownHook != null) {
      Runtime.getRuntime().removeShutdownHook(shutdownHook);
      shutdownHook = null;
    }
    if (symbolicLinkServerSocket != null) {
      OutputStream outputStream = symbolicLinkServerSocket.getOutputStream();
      outputStream.write("stop\n".getBytes(Charset.defaultCharset()));
      symbolicLinkServerSocket.close();
      symbolicLinkServerSocket = null;
    }
  }

  public void copyDirectory(final File sourceLocation, final File targetLocation,
      final CopyModeType copyMode)
          throws IOException, MojoExecutionException {

    if (sourceLocation.isDirectory()) {
      if (!targetLocation.exists()) {
        targetLocation.mkdir();
      }

      String[] children = sourceLocation.list();
      for (String element : children) {
        copyDirectory(new File(sourceLocation, element), new File(targetLocation, element),
            copyMode);
      }
    } else {
      copyFile(sourceLocation, targetLocation, copyMode);
    }
  }

  public boolean copyFile(final File source, final File target, final CopyModeType copyMode)
      throws MojoExecutionException {
    boolean fileChange = false;
    if (CopyModeType.FILE.equals(copyMode)) {
      if (target.exists() && Files.isSymbolicLink(target.toPath())) {
        target.delete();
      }
      fileChange = overCopyFile(source, target);
    } else {
      try {
        if (target.exists()) {
          Path targetPath = target.toPath();

          if (Files.isSymbolicLink(targetPath)) {

            Path symbolicLinkTarget = Files.readSymbolicLink(targetPath);
            File symbolicLinkTargetFile = symbolicLinkTarget.toFile();
            if (!symbolicLinkTargetFile.equals(source)) {
              target.delete();
              createSymbolicLink(target, source);
              fileChange = true;
            }
          } else {
            target.delete();
            createSymbolicLink(target, source);
            fileChange = true;
          }
        } else {
          createSymbolicLink(target, source);
          fileChange = true;
        }
      } catch (IOException e) {
        throw new MojoExecutionException("Could not check the target of the symbolic link "
            + target.getAbsolutePath(), e);
      }
    }
    return fileChange;
  }

  public void createSymbolicLink(final File symbolicLinkFile, final File target)
      throws MojoExecutionException {
    if (symbolicLinkServerSocket == null) {
      try {
        Files.createSymbolicLink(symbolicLinkFile.toPath(), target.toPath());
      } catch (FileSystemException e) {
        if (!isSystemSymbolicLinkCapable()) {
          throw new MojoExecutionException(
              "Could not create symbolic link and it seems that the system is"
                  + " not capable of handling symbolic links even with elevated mode",
              e);
        }
        startElevatedServer();
        if (symbolicLinkServerSocket == null) {
          throw new MojoExecutionException("Could not create symbolicLink "
              + symbolicLinkFile.getAbsolutePath()
              + " with target " + target.getAbsolutePath() + " and starting elevated server failed",
              e);
        }
      } catch (IOException e) {
        throw new MojoExecutionException(
            "Could not create symbolicLink " + symbolicLinkFile.getAbsolutePath()
                + " with target " + target.getAbsolutePath());
      }
    }
    if (symbolicLinkServerSocket != null) {
      try {
        String command = ElevatedSymbolicLinkServer.COMMAND_CREATE_SYMBOLIC_LINK + " "
            + target.toURI().toString() + " " + symbolicLinkFile.toURI().toString() + "\n";
        String response =
            PluginUtil.sendCommandToSocket(command, symbolicLinkServerSocket, "elevated-process",
                log);
        if (!"ok".equals(response)) {
          throw new MojoExecutionException("Unkonwn message from file manager server: " + response);
        }
      } catch (IOException e) {
        throw new MojoExecutionException("Could not open stream to elevated symbolic link service",
            e);
      }
    }
  }

  private int getFreePort() throws MojoExecutionException {
    final int protectedPortRange = 1024;
    int maxPortNum = 65535;
    final int relativePortRange = maxPortNum - protectedPortRange - 1;

    Random r = new Random();
    int freePort = 0;
    InetAddress localAddress;
    try {
      localAddress = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      throw new MojoExecutionException("Could not determine localhost address", e);
    }
    for (int i = 0, n = 50; (i < n) && (freePort == 0); i++) {
      int port = r.nextInt(relativePortRange) + protectedPortRange + 1;
      log.info("Trying port if it is free to start the elevated symboliclink server " + port);

      try (Socket socket = new Socket(localAddress, port)) {
        String message = "Port " + port + " is not available.";
        if (i == (n - 1)) {
          message += " Trying another one";
        } else {
          message += " This was the last try.";
        }
        log.info(message);
      } catch (IOException e) {
        freePort = port;
      }
    }

    if (freePort == 0) {
      throw new MojoExecutionException(
          "Could not find free port for elevated symbolic link service");
    }

    return freePort;
  }

  public boolean isSystemSymbolicLinkCapable() throws MojoExecutionException {
    String javaSpecVersion = System.getProperty("java.vm.specification.version");
    boolean java7Compatible = (javaSpecVersion.compareTo("1.7") >= 0);
    if (!java7Compatible) {
      log.warn("Java version must be at least 1.7 to be able to create symbolic links");
      return false;
    }
    String osname = System.getProperty("os.name").toLowerCase(Locale.getDefault());
    String osversion = System.getProperty("os.version");
    if ((osname.indexOf("win") >= 0) && (osversion.compareTo("6.0") < 0)) {
      log.warn(
          "Windows system must have version Vista or greater to be able to support symbolic links.");
      return false;
    }
    return true;
  }

  public boolean overCopyFile(final File source, final File target) throws MojoExecutionException {
    try (FileInputStream fin = new FileInputStream(source)) {
      return overCopyFile(fin, target);
    } catch (IOException e) {
      throw new MojoExecutionException("Cannot copy file " + source.getAbsolutePath() + " to "
          + target.getAbsolutePath(), e);
    }
  }

  /**
   * Copies an inputstream into a file. In case the file already exists, only those bytes are
   * overwritten in the target file that are changed.
   *
   * @param is
   *          The inputstream of the source.
   * @param targetFile
   *          The file that will be overridden if it is necessary.
   * @throws IOException
   *           if there is an error during copying the file.
   * @return true if the target file had to be changed, false if the target file was not changed.
   */
  public boolean overCopyFile(final InputStream is, final File targetFile) throws IOException {
    boolean fileChanged = false;
    boolean symbolicLink = Files.isSymbolicLink(targetFile.toPath());
    if (symbolicLink) {
      targetFile.delete();
    }
    long sum = 0;
    byte[] buffer = new byte[1024];
    try (RandomAccessFile targetRAF = new RandomAccessFile(targetFile, "rw");) {
      long originalTargetLength = targetFile.length();
      int r = is.read(buffer);
      while (r > -1) {
        sum += r;
        byte[] bytesInTarget = tryReadingAmount(targetRAF, r);
        if (!PluginUtil.isBufferSame(buffer, r, bytesInTarget)) {
          fileChanged = true;
          targetRAF.seek(targetRAF.getFilePointer() - bytesInTarget.length);
          targetRAF.write(buffer, 0, r);
        }

        r = is.read(buffer);
      }
      if (sum < originalTargetLength) {
        targetRAF.setLength(sum);
      }
    }
    return fileChanged;
  }

  public final void replaceFileWithParsed(final File parseableFile, final VelocityContext context,
      final String encoding) throws IOException, MojoExecutionException {
    VelocityEngine ve = new VelocityEngine();
    File tmpFile = File.createTempFile("eosgi-dist-parse", "tmp");
    FileOutputStream fout = null;
    FileInputStream fin = null;
    InputStreamReader reader = null;
    OutputStreamWriter writer = null;
    try {
      fin = new FileInputStream(parseableFile);
      fout = new FileOutputStream(tmpFile);
      reader = new InputStreamReader(fin);
      writer = new OutputStreamWriter(fout);
      ve.evaluate(context, writer, parseableFile.getName(), reader);
    } finally {
      if (reader != null) {
        reader.close();
      }
      if (writer != null) {
        writer.close();
      }
      if (fin != null) {
        fin.close();
      }
      if (fout != null) {
        fout.close();
      }
    }
    copyFile(tmpFile, parseableFile, CopyModeType.FILE);
    tmpFile.delete();
  }

  private void startElevatedServer() throws MojoExecutionException {
    String javaHome = System.getProperty("java.home");
    File javaExecutableFile = new File(javaHome, "bin/java.exe");
    OperatingSystem operatingSystem = OperatingSystem.instance();
    if (operatingSystem.getOperatingSystemName().toLowerCase(Locale.getDefault())
        .indexOf("win") < 0) {
      throw new MojoExecutionException(
          "Elevated symboliclink service can be started only in windows");
    }
    WindowsXPProcessManager windowsXPProcessManager = new WindowsXPProcessManager();
    URL classPathURL =
        ElevatedSymbolicLinkServer.class.getProtectionDomain().getCodeSource().getLocation();
    try {
      URI classpathURI = classPathURL.toURI();
      File classpathFile = new File(classpathURI);
      WindowsXPProcess process = (WindowsXPProcess) windowsXPProcessManager.createProcess();
      process.setTitle("Elevated symboliclink service");
      process.setLogger(Logger.getLogger("eosgi-elevated-process"));
      int port = getFreePort();
      String command = "\"" + javaExecutableFile.getAbsolutePath() + "\" -cp \""
          + classpathFile.getAbsolutePath() + "\" " + ElevatedSymbolicLinkServer.class.getName()
          + " "
          + port;
      log.info("Starting elevated symbolic link service with command: " + command);
      process.setCommand(command);
      process.startElevated();
      log.info("Symboliclink service started");

      InetAddress localHost;
      try {
        localHost = InetAddress.getLocalHost();
      } catch (UnknownHostException e) {
        throw new MojoExecutionException("Could not determine localhost", e);
      }
      for (int i = 0, n = 10; (i < n) && (symbolicLinkServerSocket == null)
          && process.isRunning(); i++) {
        try {
          symbolicLinkServerSocket = new Socket(localHost, port);
          shutdownHook = new ShutdownHook();
          Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (IOException e) {
          if (i < (n - 1)) {
            log.info("Waiting for symbolicLinkService to listen on port " + port);
            try {
              Thread.sleep(100);
            } catch (InterruptedException e1) {
              i = 10;
              Thread.currentThread().interrupt();
            }
          } else {
            log.error("Could not open port to symbolic link service on port.", e);
          }
        }
      }
      if ((symbolicLinkServerSocket == null) && !process.isRunning()) {
        log.info("Stopping symbolic link service.");
        process.stop(100, -1);
      }
    } catch (URISyntaxException e) {
      throw new MojoExecutionException("Error during starting elevated symbolic link service", e);
    }
  }

  public byte[] tryReadingAmount(final RandomAccessFile is, final int amount) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream(amount);
    byte[] buffer = new byte[amount];
    int r = is.read(buffer);
    while ((r > -1) && (bout.size() < amount)) {
      bout.write(buffer, 0, r);
      r = is.read(buffer, 0, amount - bout.size());
    }
    return bout.toByteArray();
  }

  public final void unpackZipFile(final File file, final File destinationDirectory)
      throws IOException {
    destinationDirectory.mkdirs();
    ZipFile zipFile = new ZipFile(file);

    try {
      Enumeration<? extends ZipArchiveEntry> entries = zipFile.getEntries();
      while (entries.hasMoreElements()) {
        ZipArchiveEntry entry = entries.nextElement();
        String name = entry.getName();
        File destFile = new File(destinationDirectory, name);
        if (entry.isDirectory()) {
          destFile.mkdirs();
        } else {
          File parentFolder = destFile.getParentFile();
          parentFolder.mkdirs();
          InputStream inputStream = zipFile.getInputStream(entry);
          overCopyFile(inputStream, destFile);
          FileManager.setPermissionsOnFile(destFile, entry);
        }

      }
    } finally {
      zipFile.close();
    }
  }
}
