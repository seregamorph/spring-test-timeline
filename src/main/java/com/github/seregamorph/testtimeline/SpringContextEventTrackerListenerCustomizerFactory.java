package com.github.seregamorph.testtimeline;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * @author Sergey Chernov
 */
public class SpringContextEventTrackerListenerCustomizerFactory implements ContextCustomizerFactory {

    private static final AtomicInteger contextCounter = new AtomicInteger();

    private static final String TRACKING_BEAN_NAME = SpringLifecycleTrackingBean.class.getName();

    @Nullable
    @Override
    public ContextCustomizer createContextCustomizer(
        Class<?> testClass,
        List<ContextConfigurationAttributes> configAttributes
    ) {
        return new ContextCustomizerImpl();
    }

    /**
     * Returns the id assigned to the context by this factory, or null if the context was not customized by it.
     */
    @Nullable
    static String getContextId(ApplicationContext context) {
        if (!context.containsLocalBean(TRACKING_BEAN_NAME)) {
            return null;
        }
        return context.getBean(TRACKING_BEAN_NAME, SpringLifecycleTrackingBean.class).getContextId();
    }

    private static class ContextCustomizerImpl implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            String contextId = Integer.toString(contextCounter.incrementAndGet());
            context.addApplicationListener(new SpringContextEventTrackerListener(contextId));
            // the definition is registered before the definitions of the application beans, so this singleton is
            // destroyed after all of them and can track the moment when the context is completely destroyed
            RootBeanDefinition beanDefinition = new RootBeanDefinition(SpringLifecycleTrackingBean.class);
            beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            beanDefinition.setInstanceSupplier(() -> new SpringLifecycleTrackingBean(contextId));
            ((BeanDefinitionRegistry) context).registerBeanDefinition(TRACKING_BEAN_NAME, beanDefinition);
            // the bean factory may not exist yet (refreshable contexts), so the post processor is added on refresh
            context.addBeanFactoryPostProcessor(beanFactory ->
                beanFactory.addBeanPostProcessor(new SpringContextUsageBeanPostProcessor(context)));
        }

        @Override
        public boolean equals(Object obj) {
            // we need either static singleton ContextCustomizerImpl or equals like this to produce
            // equal org.springframework.test.context.MergedContextConfiguration
            return getClass() == obj.getClass();
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
