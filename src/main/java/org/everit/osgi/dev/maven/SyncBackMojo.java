package org.everit.osgi.dev.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;

/**
 * Synchronizes back the configured directories.
 */
@Mojo(name = "sync-back", requiresProject = true)
public class SyncBackMojo extends AbstractEOSGiMojo {

  /**
   * The directory where there may be additional files to create the distribution package
   * (optional).
   */
  @Parameter(property = "eosgi.sourceDistFolder", defaultValue = "${basedir}/src/dist/")
  protected String sourceDistFolder;

  @Override
  protected void doExecute() throws MojoExecutionException, MojoFailureException {
    EnvironmentConfiguration[] environments = getEnvironmentsToProcess();
    if (environments.length != 1) {
      throw new MojoExecutionException(
          "Select exactly one environment to synchronize its folders back to the source"
              + " distribution folder! You can select an environment with the "
              + "'eosgi.environmentId' system property or 'environmentIdsToProcess' plugin"
              + " configuration.");
    }

    EnvironmentConfiguration environmentConfiguration = environments[0];
TODO
  }

}
