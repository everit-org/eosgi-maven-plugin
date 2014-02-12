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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.everit.osgi.dev.maven.BundleSettings;
import org.everit.osgi.dev.maven.DistributableArtifact;
import org.everit.osgi.dev.maven.EnvironmentConfiguration;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Artifact;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Artifacts;
import org.everit.osgi.dev.maven.jaxb.dist.definition.DistributionPackage;
import org.osgi.framework.Constants;

public class DistUtil {

    public static final String OS_LINUX_UNIX = "linux";

    public static final String OS_MACINTOSH = "mac";

    public static final String OS_SUNOS = "sunos";

    public static final String OS_WINDOWS = "windows";

    public static List<String[]> convertMapToList(final Map<String, String> map) {
        List<String[]> result = new ArrayList<>();
        for (Entry<String, String> entry : map.entrySet()) {
            String[] newEntry = new String[] { entry.getKey(), entry.getValue() };
            result.add(newEntry);
        }
        return result;
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

    public void findMatchingSettings(EnvironmentConfiguration environment, String symbolicName, String bundleVersion) {
        // Getting the start level
        List<BundleSettings> bundleSettingsList = environment.getBundleSettings();
        Iterator<BundleSettings> iterator = bundleSettingsList.iterator();
        BundleSettings matchedSettings = null;
        while (iterator.hasNext() && (matchedSettings == null)) {
            BundleSettings settings = iterator.next();
            if (settings.getSymbolicName().equals(symbolicName)
                    && ((settings.getVersion() == null) || settings.getVersion().equals(bundleVersion))) {
                matchedSettings = settings;
            }
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

    public static boolean isBufferSame(final byte[] original, final int originalLength, final byte[] target) {
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

    public static List<Artifact> getArtifactsToRemove(Map<ArtifactKey, Artifact> currentArtifactMap,
            DistributionPackage current) {
        Map<ArtifactKey, Artifact> tmpArtifactMap = new HashMap<>(currentArtifactMap);
        Artifacts artifacts = current.getArtifacts();
        if (artifacts == null) {
            return new ArrayList<>(currentArtifactMap.values());
        }
        List<Artifact> artifactList = artifacts.getArtifact();
        for (Artifact artifact : artifactList) {
            ArtifactKey artifactKey = new ArtifactKey(artifact);
            tmpArtifactMap.remove(artifactKey);
        }
        return new ArrayList<>(tmpArtifactMap.values());
    }

    public static Map<ArtifactKey, Artifact> convertDPToArtifactMap(DistributionPackage distributionPackage) {
        if (distributionPackage == null) {
            return Collections.emptyMap();
        }
        Artifacts artifacts = distributionPackage.getArtifacts();
        if (artifacts == null) {
            return Collections.emptyMap();
        }
        Map<ArtifactKey, Artifact> result = new HashMap<>();
        List<Artifact> artifactList = artifacts.getArtifact();
        for (Artifact artifact : artifactList) {
            ArtifactKey artifactKey = new ArtifactKey(artifact);
            if (result.containsKey(artifactKey)) {
                throw new DuplicateArtifactException(artifactKey);
            }
            result.put(artifactKey, artifact);
        }
        return result;
    }

    /**
     * Checking if an artifact is an OSGI bundle. An artifact is an OSGI bundle if the MANIFEST.MF file inside contains
     * a Bundle-SymbolicName.
     * 
     * @param artifact
     *            The artifact that is checked.
     * @return A {@link DistributableArtifact} with the Bundle-SymbolicName and a Bundle-Version. Bundle-Version comes
     *         from MANIFEST.MF but if Bundle-Version is not available there the default 0.0.0 version is provided.
     */
    public static DistributableArtifact processArtifact(EnvironmentConfiguration environment,
            final org.apache.maven.artifact.Artifact artifact) {
        Manifest manifest = null;

        DistributableArtifact result = new DistributableArtifact();
        result.setArtifact(artifact);
        if ("pom".equals(artifact.getType())) {
            return result;
        }
        File artifactFile = artifact.getFile();
        if ((artifactFile == null) || !artifactFile.exists()) {
            return result;
        }
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(artifactFile);
            manifest = jarFile.getManifest();
            if (manifest == null) {
                return result;
            }

            Attributes mainAttributes = manifest.getMainAttributes();
            String symbolicName = mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
            String version = mainAttributes.getValue(Constants.BUNDLE_VERSION);
            if (symbolicName != null) {
                int semicolonIndex = symbolicName.indexOf(';');
                if (semicolonIndex >= 0) {
                    symbolicName = symbolicName.substring(0, semicolonIndex);
                }
                if (version == null) {
                    version = "0.0.0";
                } else {
                    version = normalizeVersion(version);
                }

                String bundleFullName = symbolicName + ";version=" + version;
                result.setSymbolicName(symbolicName);
                result.setVersion(version);
                result.setImportPackage(mainAttributes.getValue(Constants.IMPORT_PACKAGE));
                result.setExportPackage(mainAttributes.getValue(Constants.EXPORT_PACKAGE));
                result.setFragmentHost(mainAttributes.getValue(Constants.FRAGMENT_HOST));
                return result;
            } else {
                return result;
            }

        } catch (IOException e) {
            // TODO log that this is not a jar
            return result;
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    getLog().warn("Error during closing bundleFile: " + jarFile.toString(), e);
                }
            }
        }
        // TODO DistUtil.findMatching...
    }

    /**
     * Getting the normalized version of an artifact. The artifact has to have at least three digits inside the version
     * separated by dots. If there are less than two dots inside the version it is extended with the necessary numbers
     * of ".0".
     * 
     * @param version
     *            The version that is checked.
     * @return A normalizad version.
     */
    public static String normalizeVersion(final String version) {
        int dotCount = 0;
        char[] versionCharArray = version.toCharArray();
        for (int i = 0, n = versionCharArray.length; (i < n) && (dotCount < 2); i++) {
            if (versionCharArray[i] == '.') {
                dotCount++;
            }
        }
        StringBuilder result = new StringBuilder(version);
        if (dotCount < 2) {
            result.append(".0");
        }
        if (dotCount < 1) {
            result.append(".0");
        }
        return result.toString();
    }
}
