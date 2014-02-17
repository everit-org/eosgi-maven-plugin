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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.everit.osgi.dev.maven.jaxb.dist.definition.ArtifactType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ArtifactsType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.DistributionPackageType;

public class PluginUtil {

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

    public static Map<ArtifactKey, ArtifactType> createArtifactMap(final DistributionPackageType distributionPackage) {
        if (distributionPackage == null) {
            return Collections.emptyMap();
        }
        ArtifactsType artifacts = distributionPackage.getArtifacts();
        if (artifacts == null) {
            return Collections.emptyMap();
        }
        Map<ArtifactKey, ArtifactType> result = new HashMap<>();
        List<ArtifactType> artifactList = artifacts.getArtifact();
        for (ArtifactType artifact : artifactList) {
            ArtifactKey artifactKey = new ArtifactKey(artifact);
            if (result.containsKey(artifactKey)) {
                throw new DuplicateArtifactException(artifactKey);
            }
            result.put(artifactKey, artifact);
        }
        return result;
    }

    public static void deleteFolderRecurse(final File folder) {
        if (folder.exists()) {
            File[] subFiles = folder.listFiles();
            for (File subFile : subFiles) {
                if (subFile.isDirectory()) {
                    PluginUtil.deleteFolderRecurse(subFile);
                } else {
                    subFile.delete();
                }
            }
            folder.delete();
        }
    }

    public static List<ArtifactType> getArtifactsToRemove(final Map<ArtifactKey, ArtifactType> currentArtifactMap,
            final DistributionPackageType current) {
        Map<ArtifactKey, ArtifactType> tmpArtifactMap = new HashMap<>(currentArtifactMap);
        ArtifactsType artifacts = current.getArtifacts();
        if (artifacts == null) {
            return new ArrayList<>(currentArtifactMap.values());
        }
        List<ArtifactType> artifactList = artifacts.getArtifact();
        for (ArtifactType artifact : artifactList) {
            ArtifactKey artifactKey = new ArtifactKey(artifact);
            tmpArtifactMap.remove(artifactKey);
        }
        return new ArrayList<>(tmpArtifactMap.values());
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

    private PluginUtil() {
    }
}
