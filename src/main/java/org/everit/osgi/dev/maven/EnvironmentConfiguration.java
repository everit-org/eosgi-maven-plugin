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
package org.everit.osgi.dev.maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * The OSGI environment that is specified in the pom.xml for the plugin. *
 */
public class EnvironmentConfiguration {

    /**
     * Setting non-default behaviors for bundles. For more information see the javadoc of {@link BundleSettings} class.
     * 
     */
    @Parameter
    private List<BundleSettings> bundleSettings = new ArrayList<BundleSettings>();

    /**
     * The default startlevel for newly installed bundles
     * 
     */
    @Parameter
    private Integer bundleStartLevel;

    /**
     * The name of the osgi framework. Currently equinox is supported. Default is equinox.
     * 
     */
    @Parameter
    private String framework;

    /**
     * The default start level of the OSGi framework.
     * 
     */
    @Parameter
    private Integer frameworkStartLevel;

    /**
     * The id that will be used to identify this configuration in system property of the framework.
     * 
     */
    @Parameter
    private String id;

    /**
     * System properties that will be added to the JVM of started OSGI container.
     * 
     */
    @Parameter
    private Map<String, String> systemProperties = new HashMap<String, String>();

    /**
     * The timeout in milliseconds after the Tests should stop for sure. Default value is five minutes.
     * 
     */
    @Parameter
    private long timeout = 300000;

    /**
     * The JVM options that will be applied during starting the OSGI Container.
     * 
     */
    @Parameter
    private List<String> vmOptions;

    public List<BundleSettings> getBundleSettings() {
        return bundleSettings;
    }

    public Integer getBundleStartLevel() {
        return bundleStartLevel;
    }

    public String getFramework() {
        return framework;
    }

    public Integer getFrameworkStartLevel() {
        return frameworkStartLevel;
    }

    public String getId() {
        return id;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public long getTimeout() {
        return timeout;
    }

    public List<String> getVmOptions() {
        return vmOptions;
    }

    public void setBundleSettings(final List<BundleSettings> bundleSettings) {
        this.bundleSettings = bundleSettings;
    }

    public void setBundleStartLevel(final Integer bundleStartLevel) {
        this.bundleStartLevel = bundleStartLevel;
    }

    public void setFramework(final String framework) {
        this.framework = framework;
    }

    public void setFrameworkStartLevel(final Integer frameworkStartLevel) {
        this.frameworkStartLevel = frameworkStartLevel;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public void setSystemProperties(final Map<String, String> systemProperties) {
        this.systemProperties = systemProperties;
    }

    public void setTimeout(final long timeout) {
        this.timeout = timeout;
    }

    public void setVmOptions(final List<String> vmOptions) {
        this.vmOptions = vmOptions;
    }

    @Override
    public String toString() {
        return "EnvironmentConfiguration [id=" + id + ", framework=" + framework + ", timeout=" + timeout
                + ", frameworkStartLevel=" + frameworkStartLevel + ", defaultBundleStartLevel=" + bundleStartLevel
                + ", bundleSettings=" + bundleSettings + ", vmOptions=" + vmOptions + ", systemProperties="
                + systemProperties + "]";
    }

}
