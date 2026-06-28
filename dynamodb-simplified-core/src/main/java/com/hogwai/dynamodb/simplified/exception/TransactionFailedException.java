package com.hogwai.dynamodb.simplified.exception;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when a transactional write operation is rejected by DynamoDB.
 * <p>
 * Wraps the SDK's {@link TransactionCanceledException} and exposes the
 * individual cancellation reasons for each operation in the transaction
 * in the order they were added to the builder.
 * <p>
 * Each cancellation reason corresponds to one operation in the transaction.
 * A reason of {@code null} means that operation succeeded.
 */
public class TransactionFailedException extends DynamoSimplifiedException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String NONE_CODE = "None";

    /** The per-operation cancellation reasons, one per transaction action. */
    private final List<String> cancellationReasons;

    /**
     * Creates a TransactionFailedException from an SDK TransactionCanceledException.
     *
     * @param cause the SDK exception with cancellation reasons
     */
    public TransactionFailedException(@NonNull TransactionCanceledException cause) {
        super("Transaction was canceled: " + cause.getMessage(), cause);
        this.cancellationReasons = extractCancellationReasons(cause);
    }

    private static List<String> extractCancellationReasons(TransactionCanceledException cause) {
        List<CancellationReason> reasons = cause.cancellationReasons();
        if (reasons == null) {
            return List.of();
        }
        return reasons.stream()
                .map(TransactionFailedException::mapCancellationReason)
                .toList();
    }

    private static String mapCancellationReason(CancellationReason reason) {
        if (reason != null) {
            if (NONE_CODE.equals(reason.code())) {
                return null;
            }
            return reason.code() + ": " + reason.message();
        }
        return null;
    }

    /**
     * Returns the list of cancellation reasons, one per operation in the
     * order they were added. A {@code null} entry means that operation
     * succeeded.
     *
     * @return an unmodifiable list of cancellation reason strings
     */
    @NonNull
    public List<String> getCancellationReasons() {
        return Collections.unmodifiableList(cancellationReasons);
    }

    /**
     * Returns the cancellation reason for the operation at the given index.
     *
     * @param index the operation index (0-based)
     * @return the cancellation reason, or {@code null} if the operation succeeded
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    @Nullable
    public String getCancellationReason(int index) {
        return cancellationReasons.get(index);
    }
}
