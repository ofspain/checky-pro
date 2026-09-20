package com.themistra.crypto.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.RecordComponent;

import static org.assertj.core.api.Assertions.assertThat;

/** AC5/AC8 (frozen brief), L11 — see class Javadoc on {@link KmsProperties}. */
class KmsPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(KmsProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsValidKeyId() {
        contextRunner.withPropertyValues("themistra.crypto.kms.key-id=fake-kms-key-id")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KmsProperties.class).keyId()).isEqualTo("fake-kms-key-id");
                });
    }

    @Test
    void failsWhenKeyIdMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenKeyIdBlank() {
        contextRunner.withPropertyValues("themistra.crypto.kms.key-id=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void exposesExactlyOneKeyIdentifyingField() {
        RecordComponent[] components = KmsProperties.class.getRecordComponents();
        assertThat(components).as("L11/amendment #7: no ARN + region redundancy").hasSize(1);
        assertThat(components[0].getName()).isEqualTo("keyId");
    }
}
