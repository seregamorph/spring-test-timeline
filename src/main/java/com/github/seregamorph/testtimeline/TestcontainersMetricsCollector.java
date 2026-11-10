package com.github.seregamorph.testtimeline;

import com.github.dockerjava.api.model.Container;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.testcontainers.DockerClientFactory;

public class TestcontainersMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(TestcontainersMetricsCollector.class);

    // set once the Docker environment turned out to be unavailable, no further Testcontainers calls after that
    private volatile boolean disabled = false;

    /**
     * Plain reflection instead of Spring's {@code ClassUtils}: this is called when the suite starts, and
     * spring-core may be missing from the test classpath.
     */
    public static boolean isAvailable() {
        try {
            Class.forName("org.testcontainers.DockerClientFactory", false,
                TestcontainersMetricsCollector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * @return number of containers of the current Testcontainers session or null if Docker env is not available
     */
    @Nullable
    public Integer getContainersCount() {
        if (disabled) {
            return null;
        }
        try {
            return countTestContainers(DockerClientFactory.SESSION_ID);
        } catch (RuntimeException e) {
            disabled = true;
            logger.info("Docker env is not available, skipping container counting");
            return null;
        }
    }

    @SuppressWarnings("resource")
    private static int countTestContainers(String sessionId) {
        List<Container> containers = DockerClientFactory.lazyClient()
            .listContainersCmd()
            .withShowAll(true)
            .withLabelFilter(Collections.singletonMap("org.testcontainers.sessionId", sessionId))
            .exec();
        return containers.size();
    }
}
