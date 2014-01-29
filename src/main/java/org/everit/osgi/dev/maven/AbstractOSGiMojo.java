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
     * The environments on which the tests should run.
     */
    @Parameter
    private EnvironmentConfiguration[] environments;

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
                if (processedArtifact != null) {
                    result.add(processedArtifact);
                }
            }
        }

        return result;
    }

    protected EnvironmentConfiguration getDefaultEnvironment() {
        EnvironmentConfiguration defaultEnvironment = new EnvironmentConfiguration();
        defaultEnvironment.setId("equinox");
        defaultEnvironment.setFramework("equinox");
        return defaultEnvironment;
    }

    public EnvironmentConfiguration[] getEnvironments() {
        if ((environments == null) || (environments.length == 0)) {
            environments = new EnvironmentConfiguration[] { getDefaultEnvironment() };
        }
        return environments;
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
}
