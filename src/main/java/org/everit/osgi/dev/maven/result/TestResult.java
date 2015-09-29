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
package org.everit.osgi.dev.maven.result;

/**
 * Test result that came from an OSGI test container.
 */
public class TestResult {

  /**
   * Number of errors.
   */
  private Long errorCount;

  /**
   * Number of failures.
   */
  private Long failureCount;

  /**
   * The number of tests that were ignored.
   */
  private Long ignoreCount;

  /**
   * The number of tests that were run.
   */
  private Long runCount;

  /**
   * Amount of time how long the tests were running.
   */
  private Long runTime;

  public Long getErrorCount() {
    return errorCount;
  }

  public Long getFailureCount() {
    return failureCount;
  }

  public Long getIgnoreCount() {
    return ignoreCount;
  }

  public Long getRunCount() {
    return runCount;
  }

  public Long getRunTime() {
    return runTime;
  }

  public void setErrorCount(final Long errorCount) {
    this.errorCount = errorCount;
  }

  public void setFailureCount(final Long failureCount) {
    this.failureCount = failureCount;
  }

  public void setIgnoreCount(final Long ignoreCount) {
    this.ignoreCount = ignoreCount;
  }

  public void setRunCount(final Long runCount) {
    this.runCount = runCount;
  }

  public void setRunTime(final Long runTime) {
    this.runTime = runTime;
  }

}
