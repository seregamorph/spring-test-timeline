package com.github.seregamorph.testtimeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Sergey Chernov
 */
public abstract class EventTrackerSupport {

    private static final Logger logger = LoggerFactory.getLogger(EventTrackerSupport.class);

    /**
     * System property to define the report directory explicitly, e.g. {@code -DspringTestTimelineReportDir=reports}.
     */
    public static final String REPORT_DIR_PROPERTY = "springTestTimelineReportDir";

    /**
     * Both reports are written to the build output directory ({@code target/} for Maven, {@code build/} for Gradle)
     * or the one defined via {@link #REPORT_DIR_PROPERTY}. The HTML report inlines the JSON, but the JSON is kept as
     * a separate machine-readable artifact.
     */
    private static final String JSON_REPORT_FILE_NAME = "spring-test-timeline.json";
    private static final String HTML_REPORT_FILE_NAME = "spring-test-timeline.html";

    /**
     * Number of the currently open (nested) trackings. TestNG executed via the JUnit Platform {@code testng-engine}
     * is tracked both by the JUnit Platform launcher session listener and by the TestNG execution listener, in this
     * case only the outermost pair of the calls is effective.
     */
    private static final AtomicInteger depth = new AtomicInteger();

    private static TimelineHelper timelineHelper;

    protected static void startEventTracking() {
        if (depth.getAndIncrement() > 0) {
            return;
        }
        timelineHelper = new TimelineHelper();
        timelineHelper.start();
    }

    protected static void finishEventTracking() {
        if (depth.decrementAndGet() > 0) {
            return;
        }
        // saves the report
        if (timelineHelper != null) {
            File reportDir = getReportDir();
            if (reportDir == null) {
                logger.info("Neither Maven nor Gradle build is recognized, skipping Spring test timeline report. "
                    + "Specify the report directory via -D{}=<path> system property", REPORT_DIR_PROPERTY);
                return;
            }
            if (!reportDir.isDirectory() && !reportDir.mkdirs() && !reportDir.isDirectory()) {
                logger.warn("Failed to create report directory {}, skipping Spring test timeline report",
                    reportDir.getAbsolutePath());
                return;
            }

            TimelineReportData report = timelineHelper.generateReport();
            File jsonFile = new File(reportDir, JSON_REPORT_FILE_NAME);
            byte[] reportData = JsonSerializers.serialize(report);
            MoreFileUtils.write(jsonFile, reportData);

            // the HTML report is a diagnostic artifact: a failure to render it should never
            // fail the test run that just succeeded
            try {
                File htmlFile = new File(reportDir, HTML_REPORT_FILE_NAME);
                MoreFileUtils.write(htmlFile, TimelineHtmlReport.render(reportData));
                logger.info("Spring test timeline report: {}", htmlFile.getAbsolutePath());
            } catch (RuntimeException e) {
                logger.warn("Failed to generate {}, the raw data is in {}", HTML_REPORT_FILE_NAME,
                    jsonFile.getAbsolutePath(), e);
            }
        }
    }

    /**
     * Resolves the directory for the reports: the explicit {@value #REPORT_DIR_PROPERTY} system property, otherwise
     * {@code target/} of a Maven module or {@code build/} of a Gradle project. Returns null if none is recognized.
     */
    @Nullable
    private static File getReportDir() {
        String reportDirProperty = System.getProperty(REPORT_DIR_PROPERTY);
        if (reportDirProperty != null && !reportDirProperty.trim().isEmpty()) {
            return new File(reportDirProperty.trim());
        }

        // "basedir" is provided by Maven Surefire/Failsafe, it's the module root
        String mavenBasedirProperty = System.getProperty("basedir");
        File mavenBasedir = mavenBasedirProperty == null ? new File(".") : new File(mavenBasedirProperty);
        // "org.gradle.test.worker" is set by Gradle in the forked test JVMs, the working directory is the project dir
        boolean gradleWorker = System.getProperty("org.gradle.test.worker") != null;
        File currentDir = new File(".");

        if (!gradleWorker && new File(mavenBasedir, "pom.xml").isFile()) {
            return new File(mavenBasedir, "target");
        }
        if (new File(currentDir, "build.gradle").isFile() || new File(currentDir, "build.gradle.kts").isFile()
            || gradleWorker) {
            return new File(currentDir, "build");
        }
        return null;
    }

    static void contextInitializing(String contextId) {
        if (timelineHelper == null) {
            logger.warn("timelineHelper not initialized");
            return;
        }
        timelineHelper.contextInitializing(contextId);
    }

    static void contextUsed(String contextId, String testClassName) {
        if (timelineHelper == null) {
            logger.warn("timelineHelper not initialized");
            return;
        }
        timelineHelper.contextUsed(contextId, testClassName);
    }

    static void trackEvent(String contextId, ContextEventType eventType) {
        if (timelineHelper == null) {
            logger.warn("timelineHelper not initialized");
            return;
        }
        timelineHelper.addEvent(contextId, eventType);
    }
}
