package com.github.seregamorph.testtimeline.junit;

import com.github.seregamorph.testtimeline.EventTrackerSupport;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executed once before all tests and once after all tests of the JUnit Platform execution (all engines and all
 * test plans of the session). It is auto-registered by the JUnit Platform {@code Launcher} via {@code ServiceLoader}.
 * <p>
 * The session is opened once per JVM (fork), unlike {@code TestExecutionListener#testPlanExecutionStarted} which is
 * invoked per each launched test plan.
 * <p>
 * The TestNG counterpart is {@code com.github.seregamorph.testtimeline.testng.SuiteExecutionListener}. When TestNG
 * tests are executed via the JUnit Platform {@code testng-engine}, both listeners are invoked, the nested
 * invocations are ignored by {@link EventTrackerSupport}.
 *
 * @author Sergey Chernov
 */
public class SuiteSessionListener extends EventTrackerSupport implements LauncherSessionListener {

    private static final Logger logger = LoggerFactory.getLogger(SuiteSessionListener.class);

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        logger.debug("launcherSessionOpened");
        startEventTracking();
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        logger.debug("launcherSessionClosed");
        finishEventTracking();
    }
}
