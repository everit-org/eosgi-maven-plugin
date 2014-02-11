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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
}
