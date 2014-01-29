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
