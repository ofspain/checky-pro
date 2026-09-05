package com.themistra.crypto.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** AC5 (frozen brief), L3 — see class Javadoc on {@link SnapshotProperties}. */
class SnapshotPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(SnapshotProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    private static final String[] VALID = {
            "themistra.crypto.snapshot.bucket=fake-observation-snapshots",
            "themistra.crypto.snapshot.prefix=chain-observations/",
            "themistra.crypto.snapshot.region=us-east-1"
    };

    @Test
    void bindsValidConfiguration() {
        contextRunner.withPropertyValues(VALID).run(context -> {
            assertThat(context).hasNotFailed();
            SnapshotProperties props = context.getBean(SnapshotProperties.class);
            assertThat(props.bucket()).isEqualTo("fake-observation-snapshots");
            assertThat(props.prefix()).isEqualTo("chain-observations/");
            assertThat(props.region()).isEqualTo("us-east-1");
        });
    }

    @Test
    void failsWhenBucketMissing() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.snapshot.prefix=chain-observations/",
                        "themistra.crypto.snapshot.region=us-east-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenPrefixMissing() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.snapshot.bucket=fake-observation-snapshots",
                        "themistra.crypto.snapshot.region=us-east-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenRegionMissing() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.snapshot.bucket=fake-observation-snapshots",
                        "themistra.crypto.snapshot.prefix=chain-observations/")
                .run(context -> assertThat(context).hasFailed());
    }
}
