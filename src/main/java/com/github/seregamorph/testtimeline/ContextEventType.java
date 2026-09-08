package com.github.seregamorph.testtimeline;

import java.util.Locale;

/**
 * @author Sergey Chernov
 */
public enum ContextEventType {

    /**
     * On bean creation
     */
    CREATING,
    /**
     * On ContextStartedEvent
     */
    @Deprecated // not used in tests
    STARTED,
    /**
     * On ContextRefreshedEvent event
     */
    REFRESHED,
    /**
     * On ContextPausedEvent (extends ContextStoppedEvent) event
     * Since Spring Framework 7.0
     */
    PAUSED,
    /**
     * On ContextRestartedEvent (extends ContextStartedEvent) event
     * Since Spring Framework 7.0
     */
    RESTARTED,
    /**
     * On ContextStoppedEvent event
     */
    @Deprecated // not used in tests
    STOPPED,
    /**
     * On ContextClosedEvent
     */
    CLOSED,
    /**
     * On bean destroy
     */
    DESTROYING;

    public String printableName() {
        String name = name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
