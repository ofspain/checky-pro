package com.themistra.payment.receipt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * T19 (R23/R24). Maps `receipts` in `V1__payments_baseline.sql` exactly - the signed artifact's
 * own metadata; the signed JSON/PDF objects themselves live in S3 WORM (T18's own
 * {@code ReceiptStore}). {@code paymentId} is a plain {@code Long}, never a {@code @ManyToOne}
 * to {@code Payment} (L12, Phase 3/4 Finding 10) - {@code payment} is a different feature
 * module.
 *
 * <p>Mirrors {@code VerificationRecord}'s own established shape: a protected no-arg
 * constructor, a static {@code create} factory, getters only, and an explicit
 * {@code Clock}-derived {@code createdAt} rather than relying on the column's own DB-level
 * {@code DEFAULT now()} - matches every other entity in this codebase that sets its own
 * timestamps explicitly (e.g. {@code Payment.createdAt}).</p>
 */
@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_uuid", nullable = false, unique = true)
    private UUID receiptUuid;

    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    /** {@code @JdbcTypeCode(SqlTypes.CHAR)} is load-bearing, not decorative - the DDL declares
     * {@code digest_sha256 CHAR(64)} (fixed-length, {@code bpchar}); Hibernate's default mapping
     * for a {@code String} column is {@code VARCHAR}, and {@code columnDefinition} alone only
     * affects schema generation, not the SQL type Hibernate's own validator expects at startup
     * (mirrors crypto-service's own identical, already-solved {@code Attestation.receiptDigest}
     * precedent exactly - confirmed by reading that class before guessing here). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "digest_sha256", nullable = false, length = 64)
    private String digestSha256;

    @Column(name = "signature", nullable = false)
    private String signature;

    @Column(name = "kms_key_id", nullable = false, length = 256)
    private String kmsKeyId;

    @Column(name = "s3_bucket", nullable = false, length = 128)
    private String s3Bucket;

    @Column(name = "s3_key", nullable = false, length = 256)
    private String s3Key;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Receipt() {
        // JPA only
    }

    public static Receipt create(UUID receiptUuid, Long paymentId, String digestSha256, String signature,
            String kmsKeyId, String s3Bucket, String s3Key, Instant signedAt, Instant createdAt) {
        Receipt receipt = new Receipt();
        receipt.receiptUuid = receiptUuid;
        receipt.paymentId = paymentId;
        receipt.digestSha256 = digestSha256;
        receipt.signature = signature;
        receipt.kmsKeyId = kmsKeyId;
        receipt.s3Bucket = s3Bucket;
        receipt.s3Key = s3Key;
        receipt.signedAt = signedAt;
        receipt.createdAt = createdAt;
        return receipt;
    }

    public Long getId() {
        return id;
    }

    public UUID getReceiptUuid() {
        return receiptUuid;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getDigestSha256() {
        return digestSha256;
    }

    public String getSignature() {
        return signature;
    }

    public String getKmsKeyId() {
        return kmsKeyId;
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public String getS3Key() {
        return s3Key;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
