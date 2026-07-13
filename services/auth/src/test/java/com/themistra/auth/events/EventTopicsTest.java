package com.themistra.auth.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTopicsTest {

    @Test
    void accountAggregateRoutesToUserLifecycleTopic() {
        assertThat(EventTopics.forAggregateType("account")).isEqualTo("auth.user.lifecycle");
    }

    @Test
    void unmappedAggregateTypeFailsLoudRatherThanGuessing() {
        assertThatThrownBy(() -> EventTopics.forAggregateType("unmapped-thing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unmapped-thing");
    }
}
