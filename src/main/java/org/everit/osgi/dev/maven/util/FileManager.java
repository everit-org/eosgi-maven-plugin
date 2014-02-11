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
import java.util.Enumeration;
import java.util.Random;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.everit.osgi.dev.maven.jaxb.dist.definition.CopyMode;
import org.rzo.yajsw.os.OperatingSystem;
import org.rzo.yajsw.os.ms.win.w32.WindowsXPProcess;
import org.rzo.yajsw.os.ms.win.w32.WindowsXPProcessManager;

/**
 * This class is not thread-safe. It should be used within one thread only.
 */
public class FileManager implements AutoCloseable {

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

    private static void setUnixPermissionsOnFileIfNecessary(final File file, final ZipArchiveEntry entry) {
        if (entry.getPlatform() == ZipArchiveEntry.PLATFORM_FAT) {
            return;
        }
        int unixPermissions = entry.getUnixMode();
        // Executable
        boolean doable = (unixPermissions & 0111) > 0;
        boolean doableByOthers = (unixPermissions & 011) > 0;
        file.setExecutable(doable, !doableByOthers);

        // Writeable
        doable = (unixPermissions & 0222) > 0;
        doableByOthers = (unixPermissions & 022) > 0;
        file.setWritable(doable, !doableByOthers);

        // Readable
        doable = (unixPermissions & 0444) > 0;
        doableByOthers = (unixPermissions & 044) > 0;
        file.setReadable(doable, !doableByOthers);
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
     */
    @Override
    public void close() throws IOException {
        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
        }
        if (symbolicLinkServerSocket != null) {
            OutputStream outputStream = symbolicLinkServerSocket.getOutputStream();
            outputStream.write("stop".getBytes(Charset.defaultCharset()));
            symbolicLinkServerSocket.close();
            symbolicLinkServerSocket = null;
        }
    }

    public void copyDirectory(final File sourceLocation, final File targetLocation, final CopyMode copyMode)
            throws IOException, MojoExecutionException {

        if (sourceLocation.isDirectory()) {
            if (!targetLocation.exists()) {
                targetLocation.mkdir();
            }

            String[] children = sourceLocation.list();
            for (int i = 0; i < children.length; i++) {
                copyDirectory(new File(sourceLocation, children[i]), new File(targetLocation, children[i]),
                        copyMode);
            }
        } else {
            copyFile(sourceLocation, targetLocation, copyMode);
        }
    }

    public void copyFile(final File source, final File target, final CopyMode copyMode)
            throws MojoExecutionException {
        if (CopyMode.FILE.equals(copyMode)) {
            if (target.exists() && Files.isSymbolicLink(target.toPath())) {
                target.delete();
            }
            overCopyFile(source, target);
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
                        }
                    } else {
                        target.delete();
                        createSymbolicLink(target, source);
                    }
                } else {
                    createSymbolicLink(target, source);
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Could not check the target of the symbolic link "
                        + target.getAbsolutePath(), e);
            }
        }
    }

    public void createSymbolicLink(final File symbolicLinkFile, final File target) throws MojoExecutionException {
        if (symbolicLinkServerSocket == null) {
            try {
                Files.createSymbolicLink(symbolicLinkFile.toPath(), target.toPath());
            } catch (FileSystemException e) {
                if (!isSystemSymbolicLinkCapable()) {
                    throw new MojoExecutionException("Could not create symbolic link and it seems that the system is"
                            + " not capable of handling symbolic links even with elevated mode", e);
                }
                startElevatedServer();
                if (symbolicLinkServerSocket == null) {
                    throw new MojoExecutionException("Could not create symbolicLink "
                            + symbolicLinkFile.getAbsolutePath()
                            + " with target " + target.getAbsolutePath() + " and starting elevated server failed", e);
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Could not create symbolicLink " + symbolicLinkFile.getAbsolutePath()
                        + " with target " + target.getAbsolutePath());
            }
        }
        if (symbolicLinkServerSocket != null) {
            try {
                OutputStream outputStream = symbolicLinkServerSocket.getOutputStream();
                String command = ElevatedSymbolicLinkServer.COMMAND_CREATE_SYMBOLIC_LINK + " "
                        + target.toURI().toString() + " " + symbolicLinkFile.toURI().toString();
                outputStream.write(command.getBytes(Charset.defaultCharset()));
            } catch (IOException e) {
                throw new MojoExecutionException("Could not open stream to elevated symbolic link service", e);
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
        for (int i = 0, n = 50; i < n && freePort == 0; i++) {
            int port = r.nextInt(relativePortRange) + protectedPortRange + 1;
            log.info("Trying port if it is free to start the elevated symboliclink server " + port);

            try (Socket socket = new Socket(localAddress, port)) {
                freePort = socket.getPort();
            } catch (IOException e) {
                String message = "Port " + port + " is not available.";
                if (i == n - 1) {
                    message += " Trying another one";
                } else {
                    message += " This was the last try. Message of exception is " + e.getMessage();
                }
                log.info(message);
            }
        }

        if (freePort == 0) {
            throw new MojoExecutionException("Could not find free port for elevated symbolic link service");
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
        String osname = System.getProperty("os.name").toLowerCase();
        String osversion = System.getProperty("os.version");
        if ((osname.indexOf("win") >= 0) && (osversion.compareTo("6.0") < 0)) {
            log.warn("Windows system must have version Vista or greater to be able to support symbolic links.");
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
     * Copies an inputstream into a file. In case the file already exists, only those bytes are overwritten in the
     * target file that are changed.
     * 
     * @param is
     *            The inputstream of the source.
     * @param targetFile
     *            The file that will be overridden if it is necessary.
     * @throws MojoExecutionException
     * @throws IOException
     * @throws FileNotFoundException
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
                if (!DistUtil.isBufferSame(buffer, r, bytesInTarget)) {
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
        copyFile(tmpFile, parseableFile, CopyMode.FILE);
        tmpFile.delete();
    }

    private void startElevatedServer() throws MojoExecutionException {
        String javaHome = System.getProperty("java.home");
        File javaExecutableFile = new File(javaHome, "bin/java.exe");
        OperatingSystem operatingSystem = OperatingSystem.instance();
        if (operatingSystem.getOperatingSystemName().toLowerCase().indexOf("win") < 0) {
            throw new MojoExecutionException("Elevated symboliclink service can be started only in windows");
        }
        WindowsXPProcessManager windowsXPProcessManager = new WindowsXPProcessManager();
        URL classPathURL = ElevatedSymbolicLinkServer.class.getProtectionDomain().getCodeSource().getLocation();
        try {
            URI classpathURI = classPathURL.toURI();
            File classpathFile = new File(classpathURI);
            WindowsXPProcess process = (WindowsXPProcess) windowsXPProcessManager.createProcess();
            process.setTitle("Elevated symboliclink service");
            process.setVisible(false);
            int port = getFreePort();
            String command = "\"" + javaExecutableFile.getAbsolutePath() + "\" -cp \""
                    + classpathFile.getAbsolutePath() + "\" " + ElevatedSymbolicLinkServer.class.getName() + " "
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
            for (int i = 0, n = 10; i < n && symbolicLinkServerSocket == null && process.isRunning(); i++) {
                try {
                    symbolicLinkServerSocket = new Socket(localHost, port);
                    shutdownHook = new ShutdownHook();
                    Runtime.getRuntime().addShutdownHook(shutdownHook);
                } catch (IOException e) {
                    if (i < n - 1) {
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
            if (symbolicLinkServerSocket == null && !process.isRunning()) {
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
        while (r > -1 && bout.size() < amount) {
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
                    setUnixPermissionsOnFileIfNecessary(destFile, entry);
                }

            }
        } finally {
            zipFile.close();
        }
    }
}
