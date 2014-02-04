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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.everit.osgi.dev.maven.jaxb.dist.definition.CopyMode;

public class DistUtil {

    public static final String OS_LINUX_UNIX = "linux";

    public static final String OS_MACINTOSH = "mac";

    public static final String OS_SUNOS = "sunos";

    public static final String OS_WINDOWS = "windows";

    public static void copyDirectory(final File sourceLocation, final File targetLocation, final CopyMode copyMode)
            throws IOException, MojoExecutionException {

        if (sourceLocation.isDirectory()) {
            if (!targetLocation.exists()) {
                targetLocation.mkdir();
            }

            String[] children = sourceLocation.list();
            for (int i = 0; i < children.length; i++) {
                DistUtil.copyDirectory(new File(sourceLocation, children[i]), new File(targetLocation, children[i]),
                        copyMode);
            }
        } else {
            copyFile(sourceLocation, targetLocation, copyMode);
        }
    }

    public static void copyFile(final File source, final File target, final CopyMode copyMode)
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
                            Files.createSymbolicLink(targetPath, source.toPath());
                        }
                    } else {
                        target.delete();
                        Files.createSymbolicLink(targetPath, source.toPath());
                    }
                } else {
                    Files.createSymbolicLink(target.toPath(), source.toPath());
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Could not check the target of the symbolic link "
                        + target.getAbsolutePath(), e);
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

    private static boolean isBufferSame(final byte[] original, final int originalLength, final byte[] target) {
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

    public static boolean isSymlinkCreationSupported() {
        String osname = System.getProperty("os.name").toLowerCase();
        String osversion = System.getProperty("os.version");
        return ((osname.indexOf("win") < 0) || ((osname.indexOf("win") >= 0) && (osversion.compareTo("6.0") >= 0)));
    }

    public static boolean overCopyFile(final File source, final File target) throws MojoExecutionException {
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
    public static boolean overCopyFile(final InputStream is, final File targetFile) throws IOException {
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
                if (!isBufferSame(buffer, r, bytesInTarget)) {
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

    public static final void replaceFileWithParsed(final File parseableFile, final VelocityContext context,
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
        DistUtil.copyFile(tmpFile, parseableFile, CopyMode.FILE);
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

    public static byte[] tryReadingAmount(final RandomAccessFile is, final int amount) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(amount);
        byte[] buffer = new byte[amount];
        int r = is.read(buffer);
        while (r > -1 && bout.size() < amount) {
            bout.write(buffer, 0, r);
            r = is.read(buffer, 0, amount - bout.size());
        }
        return bout.toByteArray();
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

    private DistUtil() {
    }
}
