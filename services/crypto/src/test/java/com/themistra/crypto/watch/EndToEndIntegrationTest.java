package com.themistra.crypto.watch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.FakeChainAdapter;
import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.TxResult;
import com.themistra.crypto.attest.AttestRequest;
import com.themistra.crypto.finality.FinalityPolicy;
import com.themistra.crypto.observation.Observation;
import com.themistra.crypto.observation.ObservationLog;
import com.themistra.crypto.observation.ObservationSnapshotStore;
import com.themistra.crypto.observation.FactType;
import com.themistra.crypto.provider.ProviderHealthTracker;
import com.themistra.crypto.quorum.HeldFactAlerter;
import com.themistra.crypto.quorum.QuorumDecisionService;
import com.themistra.crypto.reorg.ReorgDetector;
import com.themistra.crypto.screening.ScreeningClient;
import com.themistra.crypto.screening.ScreeningOutcome;
import com.themistra.crypto.screening.ScreeningResult;
import com.themistra.crypto.watch.dto.RegisterWatchRequest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T26 — the one real, end-to-end proof that the pieces built across T02-T25 work together, through a
 * real Postgres and a real Kafka broker, not just in isolation. Four flows: (1) register → seen →
 * confirmed → finalized → attest returns a signature; (2) a genuine 2-of-3 disagreement on a
 * non-Boolean fact holds and emits nothing further; (3) a reorg after confirmed emits
 * {@code chain.tx.reorged}; (4) a sanctioned counterparty is blocked with no signature.
 *
 * <p><b>Real infrastructure, fake providers (`agents.md`).</b> {@code PostgreSQLContainer}/
 * {@code KafkaContainer} are real; every chain observation comes from a {@link FakeChainAdapter} —
 * real RPC is never called.</p>
 *
 * <p><b>Manual `Watcher` construction, real collaborators (Frozen Brief, architectural constraint).</b>
 * {@link ProviderSet}'s constructor is hard-typed to {@code List<EthereumAdapter>}/
 * {@code List<TronAdapter>}, not the {@code ChainAdapter} interface, so a {@link FakeChainAdapter}
 * cannot be wired through the real {@link WatcherRegistry}/{@link ProviderSet} path without a
 * production change (out of this task's scope). Each flow instead constructs its own {@link Watcher}
 * directly — mirroring {@code WatcherTest}'s own established technique — but every collaborator is a
 * real, Spring-autowired bean backed by the real containers, not a mock. {@link WatcherRegistry}
 * itself is {@code @MockBean}-doubled so its own real, {@code @Scheduled} {@code reconcile()} can never
 * start a second, real-RPC-backed {@code Watcher} racing this test's own (Frozen Brief Finding #15).</p>
 *
 * <p><b>Docker availability (Phase 0-5, disclosed, not silently assumed).</b> This class was written
 * and self-reviewed to compile cleanly and to be correct by construction against the real production
 * classes it exercises. Docker was not available in the development environment this class was
 * authored in, so its actual green/red run is deferred to a Docker-available environment — stated
 * plainly in the Phase 6 implementation notes, not silently claimed as passing.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class EndToEndIntegrationTest {

    private static final String INTERNAL_SCOPE = "SCOPE_internal.crypto:write";
    private static final long CORRELATION_WINDOW_MS = 60_000L;
    private static final long FINALITY_POLL_INTERVAL_MS = 3_600_000L;
    private static final String VALID_RECIPIENT = "0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE";
    private static final String VALID_TOKEN_CONTRACT = "0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @MockBean
    private WatcherRegistry watcherRegistry;
    @MockBean
    private ObservationSnapshotStore observationSnapshotStore;
    @MockBean
    private com.themistra.crypto.attest.KmsSigner kmsSigner;
    @MockBean
    private ScreeningClient screeningClient;
    @MockBean
    private HeldFactAlerter heldFactAlerter;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ObservationLog observationLog;
    @Autowired
    private QuorumDecisionService quorumDecisionService;
    @Autowired
    private ProviderHealthTracker providerHealthTracker;
    @Autowired
    private ChainCursorRepository chainCursorRepository;
    @Autowired
    private TxLifecyclePublisher txLifecyclePublisher;
    @Autowired
    private ReorgDetector reorgDetector;
    @Autowired
    private com.themistra.crypto.events.OutboxRelay outboxRelay;
    @Autowired
    private List<FinalityPolicy> finalityPolicies;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private Clock clock;
    @Autowired
    private EntityManager entityManager;

    private KafkaConsumer<String, String> consumer;
    /** T26 self-review: {@code chain.tx.seen} and {@code chain.tx.confirmed} can legitimately arrive
     * in the same {@code poll()} batch (both facts can agree from the same delivery round). A naive
     * "poll and return on first match" helper would silently lose the second record - once a batch is
     * fetched, Kafka's consumer position has moved past every record in it regardless of whether the
     * caller inspected each one, so it is never redelivered. Every polled record is buffered here
     * instead, and searched (not just newly-polled ones) on every lookup. */
    private final List<ConsumerRecord<String, String>> receivedRecords = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("chain.tx.seen", "chain.tx.confirmed", "chain.tx.finalized",
                "chain.tx.reorged", "chain.provider.degraded"));
        // Force initial partition assignment before any test produces a record - a bare subscribe()
        // is lazy and would otherwise race the first relay() call (Frozen Brief Finding #9).
        consumer.poll(Duration.ofMillis(500));
    }

    /** Unlike {@link Watcher#stop()} (each test's own responsibility, called explicitly at the end of
     * every flow) this consumer wraps a real network connection to the Testcontainers broker, not an
     * in-process virtual-thread scheduler — closed unconditionally here so a failing assertion earlier
     * in a test still releases it. */
    @AfterEach
    void tearDown() {
        consumer.close();
    }

    private Watcher newRealWatcher(Watch watch, List<ProviderSet.NamedAdapter> adapters) {
        return new Watcher(watch, adapters, observationLog, quorumDecisionService, providerHealthTracker,
                chainCursorRepository, CORRELATION_WINDOW_MS, meterRegistry, clock, objectMapper,
                txLifecyclePublisher, finalityPolicies, FINALITY_POLL_INTERVAL_MS, reorgDetector);
    }

    private UUID registerWatch(String txHashHint, String expectedAmount) throws Exception {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_RECIPIENT, VALID_TOKEN_CONTRACT, expectedAmount, Instant.now(clock).plusSeconds(86_400));
        String responseJson = mockMvc.perform(post("/internal/v1/watches")
                        .with(jwt().authorities(new SimpleGrantedAuthority(INTERNAL_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(responseJson).get("watchId").asText());
    }

    private static TxResult tx(boolean exists, long blockNumber, BigDecimal amount, int confirmations) {
        return new TxResult(exists, "0xtxhash", "0xfrom-sanctioned-or-not", VALID_RECIPIENT,
                VALID_TOKEN_CONTRACT, amount, confirmations, blockNumber);
    }

    private static ProviderSet.NamedAdapter named(String name, FakeChainAdapter adapter) {
        return new ProviderSet.NamedAdapter(name, adapter);
    }

    /** {@code com.themistra.crypto.attest.SignatureResult} is a package-private record - invisible
     * from this test's own {@code watch} package (required for direct {@link Watcher} construction,
     * see the class Javadoc). Reflection is the narrowest fix: it avoids both a production-code
     * visibility change (out of this task's scope) and a new cross-package test-fixture file (not
     * part of the frozen brief's authorized Files to Create). {@link org.mockito.Mockito#doReturn}
     * accepts a raw {@code Object}, so the caller never needs to name the actual return type. */
    private static Object newSignatureResult(String signatureBase64, String kmsKeyId, Instant signedAt)
            throws ReflectiveOperationException {
        Class<?> signatureResultClass = Class.forName("com.themistra.crypto.attest.SignatureResult");
        var constructor = signatureResultClass.getDeclaredConstructor(String.class, String.class, Instant.class);
        constructor.setAccessible(true);
        return constructor.newInstance(signatureBase64, kmsKeyId, signedAt);
    }

    @SuppressWarnings("unchecked")
    private List<Observation> findObservations(String chain, String txHash, FactType factType) {
        return entityManager.createQuery(
                        "select o from Observation o where o.chain = :chain and o.txHash = :txHash "
                                + "and o.factType = :factType")
                .setParameter("chain", chain)
                .setParameter("txHash", txHash)
                .setParameter("factType", factType)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<ScreeningResult> findScreeningResults(String chain, String address) {
        return entityManager.createQuery(
                        "select s from ScreeningResult s where s.chain = :chain and s.address = :address")
                .setParameter("chain", chain)
                .setParameter("address", address)
                .getResultList();
    }

    private ConsumerRecord<String, String> awaitRecordOnTopic(String topic, Duration timeout) {
        Optional<ConsumerRecord<String, String>> alreadyBuffered = receivedRecords.stream()
                .filter(record -> record.topic().equals(topic))
                .findFirst();
        if (alreadyBuffered.isPresent()) {
            return alreadyBuffered.get();
        }
        Instant deadline = Instant.now(clock).plus(timeout);
        while (Instant.now(clock).isBefore(deadline)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(receivedRecords::add);
            for (ConsumerRecord<String, String> record : records) {
                if (record.topic().equals(topic)) {
                    return record;
                }
            }
        }
        return null;
    }

    private boolean noRecordAppearsOnTopic(String topic, Duration window) {
        return awaitRecordOnTopic(topic, window) == null;
    }

    @Test
    void endToEndFlowRegistersObservesAndAttestsWithASignature() throws Exception {
        UUID watchId = registerWatch("0xtxhash", "1000000");
        Watch watch = new WatchAccessor(watchId).load();

        FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM, "provider-a");
        FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM, "provider-b");
        FakeChainAdapter providerC = new FakeChainAdapter(Chain.ETHEREUM, "provider-c");
        List<ProviderSet.NamedAdapter> adapters = List.of(
                named("provider-a", providerA), named("provider-b", providerB), named("provider-c", providerC));
        Watcher watcher = newRealWatcher(watch, adapters);
        watcher.start();

        TxResult agreed = tx(true, 100L, BigDecimal.valueOf(1_000_000L), 3);
        providerA.simulateReorg("0xtxhash", agreed);
        providerB.simulateReorg("0xtxhash", agreed);
        providerC.simulateReorg("0xtxhash", agreed);
        outboxRelay.relay();

        assertThat(awaitRecordOnTopic("chain.tx.seen", Duration.ofSeconds(10))).isNotNull();
        assertThat(awaitRecordOnTopic("chain.tx.confirmed", Duration.ofSeconds(10))).isNotNull();

        assertThat(findObservations("ETHEREUM", "0xtxhash", FactType.EXISTENCE)).hasSize(3);
        assertThat(findObservations("ETHEREUM", "0xtxhash", FactType.AMOUNT)).hasSize(3);
        assertThat(quorumDecisionService.isAgreed("ETHEREUM", "0xtxhash", FactType.EXISTENCE)).isTrue();

        FinalityStatus finalStatus = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus("0xtxhash", finalStatus);
        providerB.scriptFinalityStatus("0xtxhash", finalStatus);
        providerC.scriptFinalityStatus("0xtxhash", finalStatus);
        watcher.pollFinality();
        outboxRelay.relay();

        assertThat(awaitRecordOnTopic("chain.tx.finalized", Duration.ofSeconds(10))).isNotNull();
        assertThat(quorumDecisionService.isAgreed("ETHEREUM", "0xtxhash", FactType.FINALITY)).isTrue();

        doReturn(newSignatureResult("c2ln", "test-key", Instant.now(clock))).when(kmsSigner).sign(any());

        AttestRequest attestRequest = new AttestRequest("d".repeat(64), "ETHEREUM", "0xtxhash");
        mockMvc.perform(post("/internal/v1/attest")
                        .with(jwt().authorities(new SimpleGrantedAuthority(INTERNAL_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attestRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SIGNED"))
                .andExpect(jsonPath("$.signature").value("c2ln"));

        watcher.stop();
    }

    @Test
    void disagreementOnANonBooleanFactHoldsAndEmitsNothingFurther() throws Exception {
        UUID watchId = registerWatch("0xtxhash2", "1000000");
        Watch watch = new WatchAccessor(watchId).load();

        FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM, "provider-a");
        FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM, "provider-b");
        FakeChainAdapter providerC = new FakeChainAdapter(Chain.ETHEREUM, "provider-c");
        List<ProviderSet.NamedAdapter> adapters = List.of(
                named("provider-a", providerA), named("provider-b", providerB), named("provider-c", providerC));
        Watcher watcher = newRealWatcher(watch, adapters);
        watcher.start();

        // EXISTENCE agrees for all three (a genuine 3-way split is impossible for a Boolean fact,
        // Frozen Brief Finding #3), but AMOUNT is three genuinely distinct values - no 2-of-3 majority.
        providerA.simulateReorg("0xtxhash2", tx(true, 100L, BigDecimal.valueOf(1L), 3));
        providerB.simulateReorg("0xtxhash2", tx(true, 100L, BigDecimal.valueOf(2L), 3));
        providerC.simulateReorg("0xtxhash2", tx(true, 100L, BigDecimal.valueOf(3L), 3));
        outboxRelay.relay();

        assertThat(awaitRecordOnTopic("chain.tx.seen", Duration.ofSeconds(10))).isNotNull();
        assertThat(quorumDecisionService.isAgreed("ETHEREUM", "0xtxhash2", FactType.AMOUNT)).isFalse();
        verify(heldFactAlerter).alert(eq("ETHEREUM"), eq("0xtxhash2"), eq(FactType.AMOUNT), any());

        assertThat(noRecordAppearsOnTopic("chain.tx.confirmed", Duration.ofSeconds(5))).isTrue();
        assertThat(noRecordAppearsOnTopic("chain.tx.finalized", Duration.ofSeconds(2))).isTrue();

        watcher.stop();
    }

    @Test
    void reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor() throws Exception {
        UUID watchId = registerWatch("0xtxhash3", "1000000");
        Watch watch = new WatchAccessor(watchId).load();

        FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM, "provider-a");
        FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM, "provider-b");
        FakeChainAdapter providerC = new FakeChainAdapter(Chain.ETHEREUM, "provider-c");
        List<ProviderSet.NamedAdapter> adapters = List.of(
                named("provider-a", providerA), named("provider-b", providerB), named("provider-c", providerC));
        Watcher watcher = newRealWatcher(watch, adapters);
        watcher.start();

        TxResult agreed = tx(true, 100L, BigDecimal.valueOf(1_000_000L), 3);
        providerA.simulateReorg("0xtxhash3", agreed);
        providerB.simulateReorg("0xtxhash3", agreed);
        providerC.simulateReorg("0xtxhash3", agreed);
        outboxRelay.relay();
        assertThat(awaitRecordOnTopic("chain.tx.confirmed", Duration.ofSeconds(10))).isNotNull();

        TxResult reorgedAway = tx(false, 0L, null, 0);
        providerA.scriptTx("0xtxhash3", reorgedAway);
        providerB.scriptTx("0xtxhash3", reorgedAway);
        providerC.scriptTx("0xtxhash3", reorgedAway);
        watcher.pollFinality();
        outboxRelay.relay();

        assertThat(awaitRecordOnTopic("chain.tx.reorged", Duration.ofSeconds(10))).isNotNull();

        Optional<ChainCursor> cursor = chainCursorRepository.findByWatchId(watchId);
        assertThat(cursor).isPresent();
        assertThat(cursor.get().txHash()).isNull();

        assertThat(noRecordAppearsOnTopic("chain.tx.finalized", Duration.ofSeconds(5))).isTrue();

        watcher.stop();
    }

    @Test
    void sanctionedCounterpartyIsBlockedWithNoSignature() throws Exception {
        UUID watchId = registerWatch("0xtxhash4", "1000000");
        Watch watch = new WatchAccessor(watchId).load();

        FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM, "provider-a");
        FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM, "provider-b");
        FakeChainAdapter providerC = new FakeChainAdapter(Chain.ETHEREUM, "provider-c");
        List<ProviderSet.NamedAdapter> adapters = List.of(
                named("provider-a", providerA), named("provider-b", providerB), named("provider-c", providerC));
        Watcher watcher = newRealWatcher(watch, adapters);
        watcher.start();

        TxResult agreed = tx(true, 100L, BigDecimal.valueOf(1_000_000L), 3);
        providerA.simulateReorg("0xtxhash4", agreed);
        providerB.simulateReorg("0xtxhash4", agreed);
        providerC.simulateReorg("0xtxhash4", agreed);
        outboxRelay.relay();
        assertThat(awaitRecordOnTopic("chain.tx.confirmed", Duration.ofSeconds(10))).isNotNull();

        FinalityStatus finalStatus = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus("0xtxhash4", finalStatus);
        providerB.scriptFinalityStatus("0xtxhash4", finalStatus);
        providerC.scriptFinalityStatus("0xtxhash4", finalStatus);
        watcher.pollFinality();
        outboxRelay.relay();
        assertThat(awaitRecordOnTopic("chain.tx.finalized", Duration.ofSeconds(10))).isNotNull();

        // Mirrors the real ScreeningClient contract (screening/ScreeningClient.java): every call
        // persists exactly one ScreeningResult row before returning, regardless of outcome.
        doAnswer(invocation -> {
            String chain = invocation.getArgument(0);
            String address = invocation.getArgument(1);
            String txHash = invocation.getArgument(2);
            ScreeningResult result = ScreeningResult.create(chain, address, txHash, ScreeningOutcome.BLOCKED,
                    "test-sanctions-list", "{\"match\":\"OFAC (test fixture)\"}", Instant.now(clock));
            entityManager.persist(result);
            return ScreeningOutcome.BLOCKED;
        }).when(screeningClient).screen(anyString(), anyString(), anyString());

        AttestRequest attestRequest = new AttestRequest("d".repeat(64), "ETHEREUM", "0xtxhash4");
        mockMvc.perform(post("/internal/v1/attest")
                        .with(jwt().authorities(new SimpleGrantedAuthority(INTERNAL_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attestRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("BLOCKED"));

        verify(kmsSigner, never()).sign(any());
        assertThat(findScreeningResults("ETHEREUM", "0xfrom-sanctioned-or-not"))
                .anyMatch(result -> result.outcome() == ScreeningOutcome.BLOCKED);

        watcher.stop();
    }

    /** Package-private helper reusing {@code watch}'s own visibility to load the real, persisted
     * {@link Watch} row a {@code MockMvc} registration call created, so each flow's manually-
     * constructed {@link Watcher} operates on the same entity the rest of the system sees. */
    private class WatchAccessor {
        private final UUID watchId;

        WatchAccessor(UUID watchId) {
            this.watchId = watchId;
        }

        Watch load() {
            return entityManager.find(Watch.class, watchId);
        }
    }
}
