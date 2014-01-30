/**
 * This file is part of Everit Maven OSGi plugin.
 *
 * Everit Maven OSGi plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit Maven OSGi plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit Maven OSGi plugin.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.dev.maven.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

public class DistUtil {

    public static final String OS_LINUX_UNIX = "linux";

    public static final String OS_MACINTOSH = "mac";

    public static final String OS_SUNOS = "sunos";

    public static final String OS_WINDOWS = "windows";

    public static void copyDirectory(final File sourceLocation, final File targetLocation) throws IOException {

        if (sourceLocation.isDirectory()) {
            if (!targetLocation.exists()) {
                targetLocation.mkdir();
            }

            String[] children = sourceLocation.list();
            for (int i = 0; i < children.length; i++) {
                DistUtil.copyDirectory(new File(sourceLocation, children[i]), new File(targetLocation, children[i]));
            }
        } else {

            InputStream in = new FileInputStream(sourceLocation);
            OutputStream out = new FileOutputStream(targetLocation);

            // Copy the bits from instream to outstream
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        }
    }

    public static void copyFile(final File source, final File target, final Log log) throws MojoExecutionException {
        FileInputStream fin = null;
        FileOutputStream fout = null;
        try {
            fin = new FileInputStream(source);
            fout = new FileOutputStream(target);
            DistUtil.copyStream(fin, fout, 100000);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not copy file " + source.getAbsolutePath() + " to "
                    + target.getAbsolutePath(), e);
        } finally {
            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
            if (fout != null) {
                try {
                    fout.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }
    }

    public static final void copyStream(final InputStream in, final OutputStream out, final int bufferSize)
            throws IOException {
        byte[] buffer = new byte[bufferSize];
        int read = in.read(buffer);
        while (read >= 0) {
            out.write(buffer, 0, read);
            read = in.read(buffer);
        }
    }

    private static void copyZipEntry(final ZipFile zipFile, final String entryName, final File destFile)
            throws IOException {
        ZipArchiveEntry entry = zipFile.getEntry(entryName);
        InputStream inputStream = zipFile.getInputStream(entry);
        destFile.createNewFile();
        FileOutputStream fout = null;
        try {

            fout = new FileOutputStream(destFile);
            DistUtil.copyStream(inputStream, fout, 100000);
        } finally {
            try {
                inputStream.close();
            } finally {
                if (fout != null) {
                    fout.close();
                }
            }
        }

    }

    private static File createShortCutCmdFile(final File tmpDir, final String fileName,
            final Map<File, File> sourceAndTargetFiles) throws IOException {
        File file = new File(tmpDir, fileName);
        FileOutputStream fout = new FileOutputStream(file);
        Charset defaultCharset = Charset.defaultCharset();
        try {
            OutputStreamWriter writer = new OutputStreamWriter(fout, defaultCharset);
            try {
                for (Entry<File, File> entry : sourceAndTargetFiles.entrySet()) {
                    writer.write("mklink \"" + entry.getValue().getAbsolutePath() + "\" \""
                            + entry.getKey().getAbsolutePath() + "\"\n");
                }
            } finally {
                writer.close();
            }
        } finally {

            fout.close();
        }
        return file;
    }

    public static void createSymbolicLinks(final Map<File, File> sourceAndTargetFileMap,
            final Map<String, Artifact> artifactMap, final Log log) throws MojoExecutionException {

        String javaSpecVersion = System.getProperty("java.vm.specification.version");

        boolean symbolicLinksCreated = false;
        boolean java7Compatible = (javaSpecVersion.compareTo("1.7") >= 0);
        if (java7Compatible) {
            symbolicLinksCreated = Java7SymbolicLinkUtil.createSymbolicLinks(sourceAndTargetFileMap, artifactMap, log);
        }

        if (!symbolicLinksCreated) {
            if (DistUtil.isWindowsVistaOrGreater()) {
                if (java7Compatible) {
                    log.warn("Could not create symbolic links. As this is a windows "
                            + "system trying with higher privileges.");
                } else {
                    log.info("As there is only Java 6 and windows, trying to run command line symlink creation");
                }
                try {
                    DistUtil.tryRunningWithElevate(sourceAndTargetFileMap, artifactMap, log);
                } catch (IOException e1) {
                    log.error("Could not run with elevated privileges", e1);
                    throw new MojoExecutionException("Could not create", e1);
                }
            } else if (OS_LINUX_UNIX.equals(DistUtil.getOS())) {
                if (java7Compatible) {
                    throw new MojoExecutionException("Could not create symbolic links on linux with Java 7");
                }
                log.info("As there is only Java 6 and linux, trying to run ln -s command");
                Runtime runtime = Runtime.getRuntime();
                for (Entry<File, File> entry : sourceAndTargetFileMap.entrySet()) {
                    try {
                        Process process =
                                runtime.exec(new String[] { "ln", "-s", entry.getKey().getAbsolutePath(),
                                        entry.getValue().getAbsolutePath() });
                        process.waitFor();
                        int exitCode = process.exitValue();
                        if (exitCode != 0) {
                            throw new MojoExecutionException("Exit code of command 'ln -s "
                                    + entry.getValue().getAbsolutePath() + " " + entry.getKey().getAbsolutePath()
                                    + "' returned with exit code " + exitCode);
                        }
                    } catch (IOException e) {
                        throw new MojoExecutionException("Could not create symbolic links", e);
                    } catch (InterruptedException e) {
                        throw new MojoExecutionException("Could not create symbolic links", e);
                    }
                }
            } else {
                throw new MojoExecutionException("Symbolic link generation not supported with your Java version "
                        + "andOperating systme. You need one of the followings: Java 1.7 or earlier Java with Linux /"
                        + " Windows Vista or later.");
            }
        }

    }

    public static void deleteFolderRecurse(final File folder) {
        if (folder.exists()) {
            File[] subFiles = folder.listFiles();
            for (File subFile : subFiles) {
                if (subFile.isDirectory()) {
                    DistUtil.deleteFolderRecurse(subFile);
                } else {
                    subFile.delete();
                }
            }
            folder.delete();
        }
    }

    public static String getOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.indexOf("win") >= 0) {
            return OS_WINDOWS;
        }
        if (os.indexOf("mac") >= 0) {
            return OS_MACINTOSH;
        }
        if (((os.indexOf("nix") >= 0) || (os.indexOf("nux") >= 0))) {
            return OS_LINUX_UNIX;
        }
        if (os.indexOf("sunos") >= 0) {
            return OS_SUNOS;
        }
        return null;
    }

    private static boolean isWindowsVistaOrGreater() {
        String osname = System.getProperty("os.name").toLowerCase();
        String osversion = System.getProperty("os.version");
        return ((osname.indexOf("win") >= 0) && (osversion.compareTo("6.0") >= 0));
    }

    public static final void replaceFileWithParsed(final File parseableFile, final VelocityContext context,
            final String encoding) throws IOException {
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
        fin = null;
        fout = null;
        try {
            fin = new FileInputStream(tmpFile);
            fout = new FileOutputStream(parseableFile);
            DistUtil.copyStream(fin, fout, 2000);
        } finally {
            if (fin != null) {
                fin.close();
            }
            if (fout != null) {
                fout.close();
            }
        }
        tmpFile.delete();
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

    private static void tryRunningWithElevate(final Map<File, File> sourceAndTargetFileMap,
            final Map<String, Artifact> artifactMap, final Log log) throws IOException, MojoExecutionException {
        Artifact elevateArtifact = artifactMap.get("com.jpassing:elevate");
        ZipFile elevateZipFile = new ZipFile(elevateArtifact.getFile());
        String tempDir = System.getProperty("java.io.tmpdir");
        File tmpDirFile = new File(tempDir);
        File elevateDirFile = new File(tmpDirFile, "everit-linkFolder-" + UUID.randomUUID());
        elevateDirFile.mkdirs();
        elevateDirFile.deleteOnExit();
        File elevateExeFile = new File(elevateDirFile, "elevate.exe");
        elevateExeFile.deleteOnExit();
        try {
            if (System.getProperty("os.arch").indexOf("64") >= 0) {
                log.info("We have a 64 bit operating system so copying the 64 bit elevate exe file");
                DistUtil.copyZipEntry(elevateZipFile, "bin/x64/Release/Elevate.exe", elevateExeFile);
            } else {
                log.info("We have a 32 bit operating system so copying the 32 bit elevate exe file");
                DistUtil.copyZipEntry(elevateZipFile, "bin/x64/Release/Elevate.exe", elevateExeFile);
            }
        } finally {
            elevateZipFile.close();
        }

        File cmdFile = DistUtil.createShortCutCmdFile(elevateDirFile, "dolinks.cmd", sourceAndTargetFileMap);
        cmdFile.deleteOnExit();
        ProcessBuilder processBuilder =
                new ProcessBuilder(elevateExeFile.getAbsolutePath(), "-wait", cmdFile.getAbsolutePath());
        log.info("Running: " + processBuilder.command());
        Process process = processBuilder.start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new MojoExecutionException("Could not run goal with elevated privileges", e);
        }
        cmdFile.delete();
        elevateExeFile.delete();
        elevateDirFile.delete();
    }

    public static final void unpackZipFile(final File file, final File destinationDirectory)
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
                    DistUtil.setUnixPermissionsOnFileIfNecessary(destFile, entry);
                }

            }
        } finally {
            zipFile.close();
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
    public static void overCopyFile(InputStream is, File targetFile) throws IOException {
        long sum = 0;
        byte[] buffer = new byte[1024];
        try (RandomAccessFile targetRAF = new RandomAccessFile(targetFile, "rw");) {
            long originalTargetLength = targetFile.length();
            int r = is.read(buffer);
            while (r > -1) {
                sum += r;
                byte[] bytesInTarget = tryReadingAmount(targetRAF, r);
                if (!isBufferSame(buffer, r, bytesInTarget)) {
                    targetRAF.seek(targetRAF.getFilePointer() - r);
                    targetRAF.write(buffer, 0, r);
                }

                r = is.read(buffer);
            }
            if (sum < originalTargetLength) {
                targetRAF.setLength(sum);
            }
        }
    }

    public static byte[] tryReadingAmount(RandomAccessFile is, int amount) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(amount);
        byte[] buffer = new byte[amount];
        int r = is.read(buffer);
        while (r > -1 && bout.size() < amount) {
            bout.write(buffer, 0, r);
            r = is.read(buffer, 0, amount - bout.size());
        }
        return bout.toByteArray();
    }

    private static boolean isBufferSame(byte[] original, int originalLength, byte[] target) {
        if (originalLength != target.length) {
            return false;
        }
        int i = 0;
        boolean same = true;
        while (i < originalLength && same) {
            same = original[i] == target[i];
            i++;
        }
        return same;
    }

    private DistUtil() {
    }
}
