package dev.hogwai.dynamodb.simplified.result;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.Document;
import software.amazon.awssdk.enhanced.dynamodb.MappedTableResource;

import java.util.List;

/**
 * Holds the results of a {@link dev.hogwai.dynamodb.simplified.builder.TransactGetBuilder} execution.
 * <p>
 * Provides positional access to retrieved items. Items are returned in the same order
 * as they were added to the builder. A position may yield {@code null} if the item
 * does not exist in DynamoDB.
 *
 * @param <T> the mapped table resource type
 */
public class TransactGetResults<T extends MappedTableResource<?>> {

    private final List<Document> documents;
    private final List<T> tables;

    /**
     * Constructs a results wrapper.
     *
     * @param documents the result documents from the transaction
     * @param tables    the table references (in builder order) for type-safe item extraction
     */
    public TransactGetResults(@NonNull List<Document> documents, @NonNull List<T> tables) {
        this.documents = List.copyOf(documents);
        this.tables = List.copyOf(tables);
    }

    /**
     * Returns the item at the given position in the original builder order.
     *
     * @param index the position (0-based) matching the {@code addGetItem} call order
     * @param <R>   the expected item type
     * @return the item, or {@code null} if the item does not exist
     */
    @SuppressWarnings("unchecked")
    public @Nullable <R> R get(int index) {
        if (index >= documents.size()) {
            return null;
        }
        Document doc = documents.get(index);
        if (doc == null) {
            return null;
        }
        MappedTableResource<R> table = (MappedTableResource<R>) tables.get(index);
        return doc.getItem(table);
    }

    /**
     * Returns the number of items requested in the transaction.
     *
     * @return the number of requested items
     */
    public int size() {
        return tables.size();
    }

    /**
     * Returns whether no items were requested.
     *
     * @return true if no items were requested
     */
    public boolean isEmpty() {
        return tables.isEmpty();
    }
}
