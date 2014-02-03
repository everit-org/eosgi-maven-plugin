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
