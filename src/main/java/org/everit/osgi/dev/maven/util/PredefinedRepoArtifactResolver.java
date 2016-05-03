/*
 * Copyright (C) 2011 Everit Kft. (http://everit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.osgi.dev.maven.util;

import java.util.Iterator;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Resolves maven artifacts from repositories.
 */
public class PredefinedRepoArtifactResolver {

  private final Log log;

  private final List<RemoteRepository> remoteRepositories;

  private final RepositorySystem repositorySystem;

  private final RepositorySystemSession repositorySystemSession;

  /**
   * Constructor.
   *
   * @param repositorySystem
   *          The aether repository system.
   * @param repositorySystemSession
   *          The aether repository session.
   * @param log
   *          The maven log.
   */
  public PredefinedRepoArtifactResolver(final RepositorySystem repositorySystem,
      final RepositorySystemSession repositorySystemSession,
      final List<RemoteRepository> remoteRepositories, final Log log) {
    this.repositorySystem = repositorySystem;
    this.repositorySystemSession = repositorySystemSession;
    this.remoteRepositories = remoteRepositories;
    this.log = log;
  }

  /**
   * Resolves an artifact and returns its resolved instance.
   *
   * @param pArtifactRequest
   *          The request of the artifact.
   * @return The resolved artifact.
   * @throws MojoExecutionException
   *           if anything happens.
   */
  public Artifact resolve(final ArtifactRequest pArtifactRequest) throws MojoExecutionException {
    ArtifactRequest artifactRequest =
        new ArtifactRequest(pArtifactRequest.getArtifact(), remoteRepositories, null);
    ArtifactResult artifactResult;
    try {
      artifactResult = repositorySystem.resolveArtifact(repositorySystemSession, artifactRequest);
    } catch (ArtifactResolutionException e) {
      throw new MojoExecutionException(
          "Could not resolve artifact: " + artifactRequest.getArtifact(), e);
    }
    if (!artifactResult.isResolved()) {
      List<Exception> exceptions = artifactResult.getExceptions();
      if (exceptions.size() == 0) {
        throw new MojoExecutionException(
            "Could not resolve artifact: " + artifactRequest.getArtifact());
      } else if (exceptions.size() == 1) {
        throw new MojoExecutionException(
            "Could not resolve artifact: " + artifactRequest.getArtifact(), exceptions.get(0));
      } else {
        Iterator<Exception> iterator = exceptions.iterator();
        while (iterator.hasNext()) {
          Exception exception = iterator.next();
          if (iterator.hasNext()) {
            log.error(exception);
          } else {
            throw new MojoExecutionException(
                "Could not resolve artifact: " + artifactRequest.getArtifact(), exception);
          }
        }

      }
    }
    return artifactResult.getArtifact();
  }
}
