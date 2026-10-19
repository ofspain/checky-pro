package com.themistra.payment.archtestfixtures;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.List;
import java.util.function.Function;

/**
 * T28 (frozen brief D2, Phase 3 F2/F5). A deliberate violation of
 * {@code ArchitectureTest.shouldConfineS3SdkToReceiptStoreCreateAndReadOnly}. It is a class other
 * than {@code ReceiptStore}/{@code ReceiptStoreConfig} that uses the S3 SDK, and it calls
 * operations other than {@code putObject}: directly, inside a lambda, by method reference, and
 * through the async client. It also builds its own client and depends on {@link S3Presigner}.
 * Phase 11 (Kimi Gaps 1, 2, 5) adds a {@code putObject} outside {@code ReceiptStore}, and an async
 * {@code putObject}.
 * Never referenced by production code.
 */
public class RogueS3ObjectMutator {

    public void deleteDirectly(S3Client s3) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket("b").key("k").build());
    }

    public void copyInsideALambda(S3Client s3, List<CopyObjectRequest> requests) {
        requests.forEach(request -> s3.copyObject(request));
    }

    public Function<DeleteObjectRequest, DeleteObjectResponse> deleteByMethodReference(S3Client s3) {
        return s3::deleteObject;
    }

    public void deleteAsync(S3AsyncClient s3) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket("b").key("k").build());
    }

    public S3Client buildOwnClient() {
        return S3Client.builder().build();
    }

    public void presign(S3Presigner presigner) {
        presigner.close();
    }

    public void putObjectOutsideReceiptStore(S3Client s3, PutObjectRequest request) {
        s3.putObject(request, RequestBody.empty());
    }

    public void putObjectAsync(S3AsyncClient s3, PutObjectRequest request) {
        s3.putObject(request, AsyncRequestBody.empty());
    }

    /** FU-T28-3: reads are allowed in {@code ReceiptStore} only, not anywhere else. */
    public byte[] readOutsideReceiptStore(S3Client s3, GetObjectRequest request) {
        return s3.getObjectAsBytes(request).asByteArray();
    }
}
