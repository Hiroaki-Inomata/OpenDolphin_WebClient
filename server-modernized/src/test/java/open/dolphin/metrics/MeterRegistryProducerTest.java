package open.dolphin.metrics;

import static org.junit.jupiter.api.Assertions.assertSame;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MeterRegistryProducerTest {

    private static final String PROPERTY_KEY = "open.dolphin.metrics.registry.jndi";

    @AfterEach
    void clearProperty() {
        System.clearProperty(PROPERTY_KEY);
    }

    @Test
    void fallsBackToGlobalRegistryWhenJndiLookupFails() {
        System.setProperty(PROPERTY_KEY, "java:comp/env/does-not-exist");

        MeterRegistryProducer producer = new MeterRegistryProducer();
        MeterRegistry registry = producer.produceMeterRegistry();

        assertSame(Metrics.globalRegistry, registry);
    }
}
