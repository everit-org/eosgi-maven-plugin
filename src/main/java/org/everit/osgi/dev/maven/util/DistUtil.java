package org.everit.osgi.dev.maven.util;

import org.apache.velocity.tools.generic.EscapeTool;

public final class DistUtil {

    private final EscapeTool escapeTool = new EscapeTool();

    public String propertyKey(final String key) {
        return escapeTool.propertyKey(key).replace(",", "\\,");
    }

    public String propertyValue(final String value) {
        return escapeTool.propertyKey(value).replace(",", "\\,");
    }
}
