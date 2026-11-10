package com.github.seregamorph.testtimeline.testng;

import com.github.seregamorph.testtimeline.EventTrackerSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IExecutionListener;
import org.testng.internal.RuntimeBehavior;

/**
 * Executed once before all tests and once after all tests of the TestNG execution (all suites).
 *
 * @author Sergey Chernov
 */
public class SuiteExecutionListener extends EventTrackerSupport implements IExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(SuiteExecutionListener.class);

    @Override
    public void onExecutionStart() {
        // dryRun is only true when called via junit platform testng-engine on discovery phase, there will be subsequent
        // call of this method with dryRun=false on execution phase
        if (RuntimeBehavior.isDryRun()) {
            return;
        }
        logger.debug("onExecutionStart");
        startEventTracking();
    }

    @Override
    public void onExecutionFinish() {
        if (RuntimeBehavior.isDryRun()) {
            return;
        }
        logger.debug("onExecutionFinish");
        finishEventTracking();
    }
}
