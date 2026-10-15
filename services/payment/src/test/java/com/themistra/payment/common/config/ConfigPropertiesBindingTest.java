package com.themistra.payment.common.config;

import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC1, AC5. One {@code ApplicationContextRunner} per record, each asserting both a valid binding
 * and a specific validation failure via {@code rootCause().isInstanceOf(...)
 * .hasMessageContaining(...)} - not a bare {@code hasFailed()}, which can pass for the wrong
 * reason (named lesson, crypto-service T03, re-confirmed at this task's own Phase 0/1).
 */
class ConfigPropertiesBindingTest {

    @Configuration
    @EnableConfigurationProperties(CryptoClientProperties.class)
    static class CryptoClientConfig {
    }

    @Configuration
    @EnableConfigurationProperties(InvoiceProperties.class)
    static class InvoiceConfig {
    }

    @Configuration
    @EnableConfigurationProperties(ReceiptProperties.class)
    static class ReceiptConfig {
    }

    @Configuration
    @EnableConfigurationProperties(LedgerAnchorProperties.class)
    static class LedgerAnchorConfig {
    }

    @Configuration
    @EnableConfigurationProperties(InvoiceExpirySweepProperties.class)
    static class InvoiceExpirySweepConfig {
    }

    @Test
    void cryptoClientPropertiesBindFromValidConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(CryptoClientConfig.class)
                .withPropertyValues(
                        "themistra.payment.crypto.base-url=http://crypto-service",
                        "themistra.payment.crypto.attest-path=/internal/v1/attest",
                        "themistra.payment.crypto.watch-path=/internal/v1/watches",
                        "themistra.payment.crypto.connect-timeout-ms=2000",
                        "themistra.payment.crypto.read-timeout-ms=5000",
                        "themistra.payment.crypto.auth.token-uri=http://localhost:8080/oauth2/token",
                        "themistra.payment.crypto.auth.client-id=payment-service",
                        "themistra.payment.crypto.auth.client-secret=test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CryptoClientProperties props = context.getBean(CryptoClientProperties.class);
                    assertThat(props.baseUrl()).isEqualTo("http://crypto-service");
                    assertThat(props.connectTimeoutMs()).isEqualTo(2000);
                });
    }

    @Test
    void cryptoClientPropertiesFailWhenConnectTimeoutIsNotPositive() {
        new ApplicationContextRunner().withUserConfiguration(CryptoClientConfig.class)
                .withPropertyValues(
                        "themistra.payment.crypto.base-url=http://crypto-service",
                        "themistra.payment.crypto.attest-path=/internal/v1/attest",
                        "themistra.payment.crypto.watch-path=/internal/v1/watches",
                        "themistra.payment.crypto.connect-timeout-ms=0",
                        "themistra.payment.crypto.read-timeout-ms=5000",
                        "themistra.payment.crypto.auth.token-uri=http://localhost:8080/oauth2/token",
                        "themistra.payment.crypto.auth.client-id=payment-service",
                        "themistra.payment.crypto.auth.client-secret=test-secret")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("connectTimeoutMs"));
    }

    /** Phase 11 finding 2: readTimeoutMs carries the identical @Positive annotation
     * connectTimeoutMs does above, but had no negative test of its own until now. */
    @Test
    void cryptoClientPropertiesFailWhenReadTimeoutIsNotPositive() {
        new ApplicationContextRunner().withUserConfiguration(CryptoClientConfig.class)
                .withPropertyValues(
                        "themistra.payment.crypto.base-url=http://crypto-service",
                        "themistra.payment.crypto.attest-path=/internal/v1/attest",
                        "themistra.payment.crypto.watch-path=/internal/v1/watches",
                        "themistra.payment.crypto.connect-timeout-ms=2000",
                        "themistra.payment.crypto.read-timeout-ms=0",
                        "themistra.payment.crypto.auth.token-uri=http://localhost:8080/oauth2/token",
                        "themistra.payment.crypto.auth.client-id=payment-service",
                        "themistra.payment.crypto.auth.client-secret=test-secret")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("readTimeoutMs"));
    }

    /** Phase 11 finding 1: baseUrl, attestPath, and watchPath are all @NotBlank, but none had its
     * own negative test until now - only the positive binding test and connectTimeoutMs's own
     * failure test existed for this record. */
    @Test
    void cryptoClientPropertiesFailWhenBaseUrlIsBlank() {
        new ApplicationContextRunner().withUserConfiguration(CryptoClientConfig.class)
                .withPropertyValues(
                        "themistra.payment.crypto.base-url=",
                        "themistra.payment.crypto.attest-path=/internal/v1/attest",
                        "themistra.payment.crypto.watch-path=/internal/v1/watches",
                        "themistra.payment.crypto.connect-timeout-ms=2000",
                        "themistra.payment.crypto.read-timeout-ms=5000",
                        "themistra.payment.crypto.auth.token-uri=http://localhost:8080/oauth2/token",
                        "themistra.payment.crypto.auth.client-id=payment-service",
                        "themistra.payment.crypto.auth.client-secret=test-secret")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("baseUrl"));
    }

    @Test
    void cryptoClientPropertiesFailWhenAttestPathIsBlank() {
        new ApplicationContextRunner().withUserConfiguration(CryptoClientConfig.class)
                .withPropertyValues(
                        "themistra.payment.crypto.base-url=http://crypto-service",
                        "themistra.payment.crypto.attest-path=",
                        "themistra.payment.crypto.watch-path=/internal/v1/watches",
                        "themistra.payment.crypto.connect-timeout-ms=2000",
                        "themistra.payment.crypto.read-timeout-ms=5000",
                        "themistra.payment.crypto.auth.token-uri=http://localhost:8080/oauth2/token",
                        "themistra.payment.crypto.auth.client-id=payment-service",
                        "themistra.payment.crypto.auth.client-secret=test-secret")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("attestPath"));
    }

    @Test
    void cryptoClientPropertiesFailWhenWatchPathIsBlank() {
        new ApplicationContextRunner().withUserConfiguration(CryptoClientConfig.class)
                .withPropertyValues(
                        "themistra.payment.crypto.base-url=http://crypto-service",
                        "themistra.payment.crypto.attest-path=/internal/v1/attest",
                        "themistra.payment.crypto.watch-path=",
                        "themistra.payment.crypto.connect-timeout-ms=2000",
                        "themistra.payment.crypto.read-timeout-ms=5000",
                        "themistra.payment.crypto.auth.token-uri=http://localhost:8080/oauth2/token",
                        "themistra.payment.crypto.auth.client-id=payment-service",
                        "themistra.payment.crypto.auth.client-secret=test-secret")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("watchPath"));
    }

    @Test
    void invoicePropertiesBindFromValidConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(InvoiceConfig.class)
                .withPropertyValues(
                        "themistra.payment.invoice.default-ttl-minutes=60",
                        "themistra.payment.invoice.amount-tolerance-base-units=0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    InvoiceProperties props = context.getBean(InvoiceProperties.class);
                    assertThat(props.defaultTtlMinutes()).isEqualTo(60);
                    assertThat(props.amountToleranceBaseUnits()).isEqualTo(0L);
                });
    }

    @Test
    void invoicePropertiesFailWhenDefaultTtlIsNotPositive() {
        new ApplicationContextRunner().withUserConfiguration(InvoiceConfig.class)
                .withPropertyValues(
                        "themistra.payment.invoice.default-ttl-minutes=0",
                        "themistra.payment.invoice.amount-tolerance-base-units=0")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("defaultTtlMinutes"));
    }

    /** Phase 11 finding 3: amountToleranceBaseUnits's own @PositiveOrZero had no negative test -
     * only defaultTtlMinutes's @Positive, above, was exercised failing. Zero itself is already
     * covered by invoicePropertiesBindFromValidConfiguration's own positive case. */
    @Test
    void invoicePropertiesFailWhenAmountToleranceIsNegative() {
        new ApplicationContextRunner().withUserConfiguration(InvoiceConfig.class)
                .withPropertyValues(
                        "themistra.payment.invoice.default-ttl-minutes=60",
                        "themistra.payment.invoice.amount-tolerance-base-units=-1")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("amountToleranceBaseUnits"));
    }

    @Test
    void receiptPropertiesBindFromValidConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(ReceiptConfig.class)
                .withPropertyValues(
                        "themistra.payment.receipt.bucket=local-only-fake-receipt-bucket",
                        "themistra.payment.receipt.prefix=receipts/",
                        "themistra.payment.receipt.region=us-east-1",
                        "themistra.payment.receipt.kms-public-keys-url=http://localhost:9080/local-only-fake-verification-keys")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ReceiptProperties props = context.getBean(ReceiptProperties.class);
                    assertThat(props.bucket()).isEqualTo("local-only-fake-receipt-bucket");
                    // Phase 11 finding 4: the other bound field was never independently asserted.
                    assertThat(props.kmsPublicKeysUrl()).isEqualTo("http://localhost:9080/local-only-fake-verification-keys");
                    // T18 (Phase 6): the two fields added for the S3 WORM store itself.
                    assertThat(props.prefix()).isEqualTo("receipts/");
                    assertThat(props.region()).isEqualTo("us-east-1");
                });
    }

    /** AC1, AC5: the genuinely required field - blank/missing fails startup, regardless of
     * profile (bean validation itself is not profile-aware; the operational split between local
     * and non-local lives in which value application.properties's own {@code ${VAR:}} fallback
     * resolves to, not in this validation rule - see application.properties's own comment). */
    @Test
    void receiptPropertiesFailWhenBucketIsBlank() {
        new ApplicationContextRunner().withUserConfiguration(ReceiptConfig.class)
                .withPropertyValues(
                        "themistra.payment.receipt.bucket=",
                        "themistra.payment.receipt.prefix=receipts/",
                        "themistra.payment.receipt.region=us-east-1",
                        "themistra.payment.receipt.kms-public-keys-url=http://localhost:9080/local-only-fake-verification-keys")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("bucket"));
    }

    @Test
    void receiptPropertiesFailWhenKmsPublicKeysUrlIsBlank() {
        new ApplicationContextRunner().withUserConfiguration(ReceiptConfig.class)
                .withPropertyValues(
                        "themistra.payment.receipt.bucket=local-only-fake-receipt-bucket",
                        "themistra.payment.receipt.prefix=receipts/",
                        "themistra.payment.receipt.region=us-east-1",
                        "themistra.payment.receipt.kms-public-keys-url=")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("kmsPublicKeysUrl"));
    }


    /** Phase 8 finding 1: rootCause()-style assertion, not a bare hasFailed() - matching every
     * other negative test in this file, so a context failure for an unrelated reason (e.g. a
     * broken @EnableConfigurationProperties import) is not mistaken for this field's own
     * validation firing. */
    @Test
    void receiptPropertiesFailWhenBucketIsMissingEntirely() {
        new ApplicationContextRunner().withUserConfiguration(ReceiptConfig.class)
                .withPropertyValues(
                        "themistra.payment.receipt.prefix=receipts/",
                        "themistra.payment.receipt.region=us-east-1",
                        "themistra.payment.receipt.kms-public-keys-url=http://localhost:9080/local-only-fake-verification-keys")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("bucket"));
    }

    /** Phase 8 finding 4: the symmetric "missing entirely" test receiptPropertiesFailWhenBucketIsMissingEntirely
     * already has - kms-public-keys-url is the other genuinely required field and deserves the
     * same coverage; a prefix typo or accidental rename would otherwise be caught only by the
     * blank-value test, not a missing-property one. */
    @Test
    void receiptPropertiesFailWhenKmsPublicKeysUrlIsMissingEntirely() {
        new ApplicationContextRunner().withUserConfiguration(ReceiptConfig.class)
                .withPropertyValues(
                        "themistra.payment.receipt.bucket=local-only-fake-receipt-bucket",
                        "themistra.payment.receipt.prefix=receipts/",
                        "themistra.payment.receipt.region=us-east-1")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("kmsPublicKeysUrl"));
    }

    @Test
    void ledgerAnchorPropertiesBindFromValidConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(LedgerAnchorConfig.class)
                .withPropertyValues("themistra.payment.ledger.anchor.cron=0 3 * * *")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LedgerAnchorProperties props = context.getBean(LedgerAnchorProperties.class);
                    assertThat(props.cron()).isEqualTo("0 3 * * *");
                });
    }

    @Test
    void ledgerAnchorPropertiesFailWhenCronIsBlank() {
        new ApplicationContextRunner().withUserConfiguration(LedgerAnchorConfig.class)
                .withPropertyValues("themistra.payment.ledger.anchor.cron=")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("cron"));
    }

    @Test
    void invoiceExpirySweepPropertiesBindFromValidConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(InvoiceExpirySweepConfig.class)
                .withPropertyValues("themistra.payment.invoice.expiry-sweep.cron=0 */5 * * *")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    InvoiceExpirySweepProperties props = context.getBean(InvoiceExpirySweepProperties.class);
                    assertThat(props.cron()).isEqualTo("0 */5 * * *");
                });
    }

    @Test
    void invoiceExpirySweepPropertiesFailWhenCronIsBlank() {
        new ApplicationContextRunner().withUserConfiguration(InvoiceExpirySweepConfig.class)
                .withPropertyValues("themistra.payment.invoice.expiry-sweep.cron=")
                .run(context -> assertThat(context).getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class)
                        .hasMessageContaining("cron"));
    }
}
