package org.everit.osgi.dev.maven.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

public class Java7SymbolicLinkUtil {

    public static boolean createSymbolicLinks(final Map<File, File> sourceAndTargetFileMap,
            final Map<String, Artifact> artifactMap, final Log log) throws MojoExecutionException {
        try {
            for (Entry<File, File> entry : sourceAndTargetFileMap.entrySet()) {
                Files.createSymbolicLink(entry.getValue().toPath(), entry.getKey().toPath());
            }
            return true;
        } catch (FileSystemException e) {
            log.warn("Unable to create symbolic links with java 7 api.", e);
            return false;
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to create symbolic links", e);

        }
    }
}
