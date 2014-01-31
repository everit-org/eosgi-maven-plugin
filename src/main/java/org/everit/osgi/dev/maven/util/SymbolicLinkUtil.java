package org.everit.osgi.dev.maven.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;

public class SymbolicLinkUtil {

    private void checkSystemSymbolicLinkCapable() throws MojoExecutionException {
        String javaSpecVersion = System.getProperty("java.vm.specification.version");
        boolean java7Compatible = (javaSpecVersion.compareTo("1.7") >= 0);
        if (!java7Compatible) {
            throw new MojoExecutionException("Java version must be at least 1.7 to be able to create symbolic links");
        }
        String osname = System.getProperty("os.name").toLowerCase();
        String osversion = System.getProperty("os.version");
        if ((osname.indexOf("win") >= 0) && (osversion.compareTo("6.0") < 0)) {
            throw new MojoExecutionException(
                    "Windows system must have version Vista or greater to be able to support symbolic links.");
        }
        createTestSymbolicLink();
    }

    public void createSymbolicLink(final File symbolicLinkFile, final File target) throws IOException {
        Files.createSymbolicLink(symbolicLinkFile.toPath(), target.toPath());
    }

    private void createTestSymbolicLink() throws MojoExecutionException {
        File tempFile = null;
        File tmpSymbolicLinkFile = null;
        try {
            tempFile = File.createTempFile("eosgi-", "-testFile");
            tmpSymbolicLinkFile = File.createTempFile("eosgi-", "-testSymbolicLink");
            tmpSymbolicLinkFile.delete();

            Path tmpSymbolicLinkPath = tmpSymbolicLinkFile.toPath();
            createSymbolicLink(tmpSymbolicLinkFile, tempFile);

            Path originalPath = Files.readSymbolicLink(tmpSymbolicLinkPath);
            File originalFile = originalPath.toFile();
            if (!originalFile.equals(tempFile)) {
                throw new MojoExecutionException("It seems that the system cannot handle symbolic links. "
                        + "Error during checking test symbolic links. " + tempFile + " and " + originalFile
                        + " should be the same.");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Symbolic link support check failed during creating test link", e);
        } finally {
            if (tmpSymbolicLinkFile != null) {
                tmpSymbolicLinkFile.delete();
            }
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    public void init() throws MojoExecutionException {
        checkSystemSymbolicLinkCapable();
    }

    /**
     * In case an elevated window was started, it will be stopped by calling this function.
     */
    public void stop() {
        // TODO
    }
}
