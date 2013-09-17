package org.everit.osgi.dev.maven;

/*
 * Copyright (c) 2011, Everit Kft.
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

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
import org.apache.maven.project.MavenProject;
import org.osgi.framework.Constants;

/**
 * Abstract Mojo that collects the OSGI bundle dependencies of the current projects except scope provided.
 * 
 * @requiresProject true
 * @requiresDependencyResolution test
 */
public abstract class AbstractOSGIMojo extends AbstractMojo {

    /**
     * The environments on which the tests should run.
     * 
     * @parameter
     */
    private EnvironmentConfiguration[] environments;

    /**
     * Map of plugin artifacts.
     * 
     * @parameter expression="${plugin.artifactMap}"
     * @required
     * @readonly
     */
    protected Map<String, Artifact> pluginArtifactMap;

    /**
     * The Maven project.
     * 
     * @parameter expression="${project}"
     * @readonly
     * @required
     */
    protected MavenProject project;

    /**
     * Checking if an artifact is an OSGI bundle. An artifact is an OSGI bundle if the MANIFEST.MF file inside contains
     * a Bundle-SymbolicName.
     * 
     * @param artifact
     *            The artifact that is checked.
     * @return A {@link BundleArtifact} with the Bundle-SymbolicName and a Bundle-Version. Bundle-Version comes from
     *         MANIFEST.MF but if Bundle-Version is not available there the default 0.0.0 version is provided.
     */
    protected BundleArtifact checkBundle(final Artifact artifact) {
        if ("pom".equals(artifact.getType())) {
            getLog().debug(
                    "Artifact ["
                            + artifact.getId()
                            + "] is a pom therefore it will be excluded from the OSGI bundles. "
                            + "The dependencies of this dependency will be resolved transitively.");
            return null;
        }
        File bundleFile = artifact.getFile();
        if ((bundleFile == null) || !bundleFile.exists()) {
            getLog().warn(
                    "Bundle does not exist (it will be excluded from the bundles in the OSGI integration test): "
                            + artifact.getArtifactId());
            return null;
        }
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(bundleFile);
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                getLog().warn("Bundle does not have a manifest "
                        + "(it will be excluded from the bundles in the OSGI integration test): "
                        + bundleFile.toString());
                return null;
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
                getLog().debug("Found bundle: " + bundleFullName);
                BundleArtifact bundleArtifact = new BundleArtifact();
                bundleArtifact.setArtifact(artifact);
                bundleArtifact.setSymbolicName(symbolicName);
                bundleArtifact.setVersion(version);
                bundleArtifact.setImportPackage(mainAttributes.getValue(Constants.IMPORT_PACKAGE));
                bundleArtifact.setExportPackage(mainAttributes.getValue(Constants.EXPORT_PACKAGE));
                bundleArtifact.setFragmentHost(mainAttributes.getValue(Constants.FRAGMENT_HOST));
                bundleArtifact.setManifest(manifest);
                return bundleArtifact;
            } else {
                getLog().warn(
                        "Bundle is not valid (it will be excluded from the bundles in the OSGI integration test): "
                                + bundleFile.toString());
                return null;
            }

        } catch (IOException e) {
            getLog().warn(
                    "Bundle artifact could not be read (it will be excluded from the bundles): "
                            + bundleFile.toString(), e);
            return null;
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    getLog().warn(
                            "Error during closing bundleFile: "
                                    + jarFile.toString(), e);
                }
            }
        }
    }

    /**
     * Getting the bundle artifacts of the project. The artifact list is calculated each time when the function is
     * called therefore the developer should not call it inside an iteration.
     * 
     * @param includeCurrentProject
     *            Whether to try including the artifact of the current project or not.
     * @return The list of dependencies that are OSGI bundles but do not have the scope "provided"
     * @throws MalformedURLException
     *             if the URL for the artifact is broken.
     */
    protected List<BundleArtifact> getBundleArtifacts(final boolean includeCurrentProject,
            final boolean includeTestRunner) throws MalformedURLException {
        @SuppressWarnings("unchecked")
        List<Artifact> availableArtifacts = new ArrayList<Artifact>(project.getArtifacts());
        if (includeCurrentProject) {
            availableArtifacts.add(project.getArtifact());
        }

        if (includeTestRunner) {
            Artifact junit4RunnerArtifact = pluginArtifactMap
                    .get("org.everit.osgi.dev:org.everit.osgi.dev.testrunner");
            availableArtifacts.add(junit4RunnerArtifact);
            Artifact junit4Artifact = pluginArtifactMap.get("org.junit:com.springsource.org.junit");
            availableArtifacts.add(junit4Artifact);
        }

        boolean slf4jImplAvailable = false;
        boolean xmlcommonsAvailable = false;
        boolean trackerAvailable = false;
        List<BundleArtifact> result = new ArrayList<BundleArtifact>();
        for (Artifact artifact : availableArtifacts) {
            if (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
                BundleArtifact bundleArtifact = checkBundle(artifact);
                if (bundleArtifact != null) {
                    result.add(bundleArtifact);

                    String exportPackage = bundleArtifact.getExportPackage();
                    if (exportPackage != null) {
                        if (exportPackage.contains("org.slf4j.impl")) {
                            slf4jImplAvailable = true;
                        } else if (bundleArtifact.getSymbolicName().equals(
                                "org.everit.osgi.bundles.org.apache.xmlcommons.full")) {
                            xmlcommonsAvailable = true;
                        } else if (bundleArtifact.getSymbolicName().contains("org.osgi.util.tracker")) {
                            trackerAvailable = true;
                        }
                    }
                }
            }
        }

        if (includeTestRunner && !slf4jImplAvailable) {
            result.add(checkBundle(pluginArtifactMap.get("org.slf4j:slf4j-simple")));
            result.add(checkBundle(pluginArtifactMap.get("org.slf4j:slf4j-api")));
        }
        if (includeTestRunner && !xmlcommonsAvailable) {
            result.add(checkBundle(pluginArtifactMap
                    .get("org.everit.osgi.bundles:org.everit.osgi.bundles.org.apache.xmlcommons.full")));
        }
        if (includeTestRunner && !trackerAvailable) {
            result.add(checkBundle(pluginArtifactMap
                    .get("org.everit.osgi.bundles:org.everit.osgi.bundles.org.osgi.util.tracker")));
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
