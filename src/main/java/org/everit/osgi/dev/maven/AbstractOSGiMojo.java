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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.osgi.framework.Constants;

/**
 * Abstract Mojo that collects the OSGI bundle dependencies of the current projects except scope provided.
 */
public abstract class AbstractOSGiMojo extends AbstractMojo {

    /**
     * Comma separated list of the id of the environments that should be processed. Default is * that means all
     * environments.
     */
    @Parameter(property = "eosgi.environmentIds", defaultValue = "*")
    protected String environmentIds = "*";

    /**
     * The environments on which the tests should run.
     */
    @Parameter
    protected EnvironmentConfiguration[] environments;

    private EnvironmentConfiguration[] environmentsToProcess;

    /**
     * Map of plugin artifacts.
     */
    @Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
    protected Map<String, Artifact> pluginArtifactMap;

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    protected EnvironmentConfiguration getDefaultEnvironment() {
        getLog().info("There is no environment specified in the project. Creating equinox environment with"
                + " default settings");
        EnvironmentConfiguration defaultEnvironment = new EnvironmentConfiguration();
        defaultEnvironment.setId("equinox");
        defaultEnvironment.setFramework("equinox");
        return defaultEnvironment;
    }

    public EnvironmentConfiguration[] getEnvironments() {
        if (environments == null || environments.length == 0) {
            environments = new EnvironmentConfiguration[] { getDefaultEnvironment() };
        }
        return environments;
    }

    /**
     * Getting an array of the environment configurations that should be processed based on the value of the
     * {@link #environmentIds} parameter. The value, that is returned, is calculated the first time the function is
     * called.
     * 
     * @return The array of environment ids that should be processed.
     */
    protected EnvironmentConfiguration[] getEnvironmentsToProcess() {
        if (environmentsToProcess != null) {
            return environmentsToProcess;
        }

        if ("*".equals(environmentIds)) {
            environmentsToProcess = getEnvironments();
        } else {
            String[] environmentIdArray = environmentIds.trim().split(",");

            EnvironmentConfiguration[] tmpEnvironments = getEnvironments();

            List<EnvironmentConfiguration> result = new ArrayList<EnvironmentConfiguration>();
            for (int i = 0; i < tmpEnvironments.length; i++) {
                boolean found = false;
                int j = 0, n = environmentIdArray.length;
                while (!found && j < n) {
                    if (environmentIdArray[j].equals(tmpEnvironments[j].getId())) {
                        found = true;
                        result.add(tmpEnvironments[i]);
                    }
                    j++;
                }
            }
            environmentsToProcess = result.toArray(new EnvironmentConfiguration[result.size()]);
        }
        return environmentsToProcess;
    }

    /**
     * Getting the processed artifacts of the project. The artifact list is calculated each time when the function is
     * called therefore the developer should not call it inside an iteration.
     * 
     * @return The list of dependencies that are OSGI bundles but do not have the scope "provided"
     * @throws MalformedURLException
     *             if the URL for the artifact is broken.
     */
    protected List<ProcessedArtifact> getProcessedArtifacts() throws MalformedURLException {
        @SuppressWarnings("unchecked")
        List<Artifact> availableArtifacts = new ArrayList<Artifact>(project.getArtifacts());
        availableArtifacts.add(project.getArtifact());

        List<ProcessedArtifact> result = new ArrayList<ProcessedArtifact>();
        for (Artifact artifact : availableArtifacts) {
            if (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
                ProcessedArtifact processedArtifact = processArtifact(artifact);
                result.add(processedArtifact);
            }
        }
        return result;
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
    protected String normalizeVersion(final String version) {
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

    /**
     * Checking if an artifact is an OSGI bundle. An artifact is an OSGI bundle if the MANIFEST.MF file inside contains
     * a Bundle-SymbolicName.
     * 
     * @param artifact
     *            The artifact that is checked.
     * @return A {@link ProcessedArtifact} with the Bundle-SymbolicName and a Bundle-Version. Bundle-Version comes from
     *         MANIFEST.MF but if Bundle-Version is not available there the default 0.0.0 version is provided.
     */
    protected ProcessedArtifact processArtifact(final Artifact artifact) {
        ProcessedArtifact result = new ProcessedArtifact();
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
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                return result;
            }
            result.setManifest(manifest);

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
                getLog().debug("Found bundle: " + bundleFullName);
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
            getLog().warn(
                    "Bundle artifact could not be read (it will be handled as an ordinary artifact): "
                            + artifactFile.toString(), e);
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
    }
}
