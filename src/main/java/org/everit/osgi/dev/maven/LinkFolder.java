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
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.everit.osgi.dev.maven.util.DistUtil;

/**
 * This goal is deprecated. Please use the goal "dist" instead.
 * 
 * Creates a folder that contains symbolic links to the dependent target artifacts located in the local Maven
 * repository. Please note that this feature needs Java version 1.7 as NIO.2 API is used. 
 * 
 * @goal linkFolder
 * @phase package
 * @requiresProject true
 * @requiresDependencyResolution test
 * @execute phase="package"
 * @deprecated The linkFolder goal is deprecated. Please use the dist goal instead.
 */
public class LinkFolder extends AbstractOSGIMojo {

    /**
     * @parameter expression="${plugin.pluginArtifact}"
     */
    protected Artifact pluginArtifact;

    /**
     * @parameter expression="${executedProject}"
     */
    protected MavenProject executedProject;

    /**
     * Whether to include the test runner and it's dependencies.
     * 
     * @parameter expression="${eosgi.includeTestRunner}" default-value="false"
     */
    protected boolean includeTestRunner = false;

    /**
     * Whether to include the artifact of the current project or not. If false only the dependencies will be processed.
     * 
     * @parameter expression="${eosgi.includeCurrentProject}" default-value="false"
     */
    protected boolean includeCurrentProject = false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // File projectArtifactFile = project.getArtifact().getFile();
        // if (projectArtifactFile == null) {
        // throw new MojoExecutionException("package phase has to run before this the linkFolder goal");
        // }
        
        getLog().warn("THIS GOAL IS DEPRECATED!!! USE THE dist GOAL INSTEAD");
        String buildDirectory = project.getBuild().getDirectory();
        File buildDirectoryFile = new File(buildDirectory);
        File dependencyFolder = new File(buildDirectoryFile, "bundleDependencies");
        dependencyFolder.mkdirs();
        List<BundleArtifact> bundleArtifacts = null;
        try {
            bundleArtifacts = getBundleArtifacts(includeCurrentProject, includeTestRunner);
            Artifact executedProjectArtifact = executedProject.getArtifact();
            BundleArtifact bundleArtifact = checkBundle(executedProjectArtifact);
            if (bundleArtifact != null) {
                bundleArtifacts.add(bundleArtifact);
            }
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Could not resolve dependency bundles", e);
        }
        Map<File, File> linkMap = new HashMap<File, File>();
        for (BundleArtifact bundleArtifact : bundleArtifacts) {
            Artifact artifact = bundleArtifact.getArtifact();
            File artifactFile = artifact.getFile();
            File symlinkFile = new File(dependencyFolder, artifactFile.getName());
            if (!symlinkFile.exists()) {
                linkMap.put(artifactFile, symlinkFile);
            }
        }
        DistUtil.createSymbolicLinks(linkMap, pluginArtifactMap, getLog());
    }

}
