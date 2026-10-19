package com.github.seregamorph.testtimeline;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;

/**
 * Records every test class that uses a Spring context, including the classes that reuse a cached context created by
 * another test class.
 * <p>
 * The {@code DependencyInjectionTestExecutionListener} initializes each test instance via
 * {@link AutowireCapableBeanFactory#initializeBean} with the bean name
 * {@code <test class name>}{@value AutowireCapableBeanFactory#ORIGINAL_INSTANCE_SUFFIX}, which applies the bean post
 * processors of the context. It's done on the initial injection and on the re-injection after a method-level
 * {@code @DirtiesContext}.
 * <p>
 * Unlike a {@code TestExecutionListener} registered in {@code spring.factories}, this works even if the test class
 * hierarchy declares {@code @TestExecutionListeners} without merging with the defaults.
 *
 * @author Sergey Chernov
 */
class SpringContextUsageBeanPostProcessor implements BeanPostProcessor {

    private final ApplicationContext context;

    SpringContextUsageBeanPostProcessor(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (beanName.endsWith(AutowireCapableBeanFactory.ORIGINAL_INSTANCE_SUFFIX)) {
            String testClassName = bean.getClass().getName();
            if (beanName.equals(testClassName + AutowireCapableBeanFactory.ORIGINAL_INSTANCE_SUFFIX)) {
                trackContextUsage(testClassName);
            }
        }
        return bean;
    }

    private void trackContextUsage(String testClassName) {
        // with @ContextHierarchy the test class uses the parent contexts as well
        for (ApplicationContext ctx = context; ctx != null; ctx = ctx.getParent()) {
            String contextId = SpringContextEventTrackerListenerCustomizerFactory.getContextId(ctx);
            if (contextId != null) {
                EventTrackerSupport.contextUsed(contextId, testClassName);
            }
        }
    }
}
