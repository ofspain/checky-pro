package com.themistra.notification.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit, no Spring context, no Docker.
 */
class FakeEmailTransportTest {

    private final FakeEmailTransport transport = new FakeEmailTransport();

    @BeforeEach
    void clearCaptures() {
        transport.clear();
    }

    private static EmailMessage message(String to) {
        return new EmailMessage(UUID.randomUUID(), to, "no-reply@checky.pro", "subject", "body");
    }

    @Test
    void sendCapturesTheMessageAndReturnsANonNullId() {
        String id = transport.send(message("a@example.com"));

        assertThat(id).isNotNull().isNotBlank();
        assertThat(transport.sentMessages()).hasSize(1);
        assertThat(transport.sentMessages().get(0).to()).isEqualTo("a@example.com");
    }

    @Test
    void sentMessagesReturnsAnUnmodifiableView() {
        transport.send(message("a@example.com"));

        assertThat(transport.sentMessages()).hasSize(1);
        assertThatCodeThrowsUnsupported(() -> transport.sentMessages().add(message("b@example.com")));
    }

    private static void assertThatCodeThrowsUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected - sentMessages() must not be mutable from outside
        }
    }

    @Test
    void clearEmptiesTheCaptureList() {
        transport.send(message("a@example.com"));

        transport.clear();

        assertThat(transport.sentMessages()).isEmpty();
    }

    @Test
    void findMostRecentByRecipientReturnsEmptyWhenNothingSent() {
        assertThat(transport.findMostRecentByRecipient("a@example.com")).isEmpty();
    }

    @Test
    void findMostRecentByRecipientReturnsTheOnlyMatch() {
        transport.send(message("a@example.com"));

        assertThat(transport.findMostRecentByRecipient("a@example.com"))
                .isPresent().get().extracting(EmailMessage::to).isEqualTo("a@example.com");
    }

    /** Kimi Phase 8 Finding #5: the method's own documented "most recent, not first" semantics. */
    @Test
    void findMostRecentByRecipientReturnsTheLatestWhenSentMultipleTimesToTheSameRecipient() {
        transport.send(new EmailMessage(UUID.randomUUID(), "a@example.com", "no-reply@checky.pro",
                "first subject", "first body"));
        transport.send(new EmailMessage(UUID.randomUUID(), "a@example.com", "no-reply@checky.pro",
                "second subject", "second body"));

        EmailMessage mostRecent = transport.findMostRecentByRecipient("a@example.com").orElseThrow();

        assertThat(mostRecent.subject()).isEqualTo("second subject");
    }

    @Test
    void findMostRecentByRecipientIgnoresNonMatchingRecipients() {
        transport.send(message("a@example.com"));

        assertThat(transport.findMostRecentByRecipient("b@example.com")).isEmpty();
    }

    /** Kimi Phase 3 Finding #2 (AC10): thread-safety proof - EmailChannel is a singleton called
     * concurrently from Kafka listener container threads, mirroring
     * IdempotencyGuardIntegrationTest.concurrentCallsWithSameKeyResolveToExactlyOneTrue's own
     * established concurrent-call pattern. */
    @Test
    void concurrentSendsAreAllCapturedWithoutLoss() throws InterruptedException {
        int threadCount = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                transport.send(message("recipient-" + index + "@example.com"));
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        boolean finishedCleanly = pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finishedCleanly).as("all threads must finish without hanging").isTrue();
        List<EmailMessage> sent = transport.sentMessages();
        assertThat(sent).hasSize(threadCount);
        assertThat(sent.stream().map(EmailMessage::to).distinct()).hasSize(threadCount);
    }
}
