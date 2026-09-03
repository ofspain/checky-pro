package com.themistra.crypto.quorum;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.themistra.crypto.observation.FactType;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** AC2/AC7 (ops alert on HELD, interim log-based implementation). Mirrors
 * ObservationSnapshotStoreTest's ListAppender pattern (T08). */
class HeldFactAlerterTest {

    private final HeldFactAlerter alerter = new HeldFactAlerter();

    @Test
    void alertLogsAtErrorLevelWithChainTxHashFactTypeAndEveryProviderAnswer() {
        Logger logger = (Logger) LoggerFactory.getLogger(HeldFactAlerter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            List<ProviderAnswer<Boolean>> answers = List.of(
                    new ProviderAnswer<>("alchemy", true),
                    new ProviderAnswer<>("quicknode", false),
                    new ProviderAnswer<>("infura", true));

            alerter.alert("ETHEREUM", "0xabc123", FactType.EXISTENCE, answers);

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            String message = event.getFormattedMessage();
            assertThat(message)
                    .contains("ETHEREUM")
                    .contains("0xabc123")
                    .contains("EXISTENCE")
                    .contains("alchemy")
                    .contains("quicknode")
                    .contains("infura");
        } finally {
            logger.detachAppender(appender);
        }
    }
}
