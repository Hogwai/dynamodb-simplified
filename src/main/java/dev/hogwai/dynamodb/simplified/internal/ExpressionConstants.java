package dev.hogwai.dynamodb.simplified.internal;

/**
 * Shared constants for DynamoDB expression syntax: placeholder names,
 * logical operators, and function names.
 */
public final class ExpressionConstants {

    // region Entity key component names

    /** Entity key component name for partition key ({@code @KeyComponent(component = "PK")}). */
    public static final String PK_COMPONENT = "PK";

    /** Entity key component name for sort key ({@code @KeyComponent(component = "SK")}). */
    public static final String SK_COMPONENT = "SK";

    // endregion

    // region Expression attribute placeholders

    /** Partition key expression attribute name placeholder. */
    public static final String PK = "#pk";

    /** Sort key expression attribute name placeholder. */
    public static final String SK = "#sk";

    /** Discriminator expression attribute name placeholder. */
    public static final String DT = "#dt";

    /** Partition key expression attribute value placeholder. */
    public static final String PK_VAL = ":pk0";

    /** Sort key (lower bound) expression attribute value placeholder. */
    public static final String SK_VAL0 = ":sk0";

    /** Sort key (upper bound) expression attribute value placeholder. */
    public static final String SK_VAL1 = ":sk1";

    // endregion

    // region Logical operators

    /** AND operator with surrounding spaces. */
    public static final String AND = " AND ";

    /** OR operator with surrounding spaces. */
    public static final String OR = " OR ";

    // endregion

    // region Comparison operators

    public static final String LE = " <= ";
    public static final String GE = " >= ";

    // endregion

    // region DynamoDB function names

    /** {@code begins_with(path, substr)} function name. */
    public static final String BEGINS_WITH = "begins_with(";

    /** {@code contains(path, operand)} function name. */
    public static final String CONTAINS = "contains(";

    /** {@code attribute_exists(path)} function name. */
    public static final String ATTRIBUTE_EXISTS = "attribute_exists(";

    /** {@code attribute_not_exists(path)} function name. */
    public static final String ATTRIBUTE_NOT_EXISTS = "attribute_not_exists(";

    /** {@code size(path)} function name. */
    public static final String SIZE = "size(";

    /** {@code if_not_exists(path, default)} function name. */
    public static final String IF_NOT_EXISTS = "if_not_exists(";

    /** {@code list_append(list1, list2)} function name. */
    public static final String LIST_APPEND = "list_append(";

    private ExpressionConstants() {
    }
}
// endregion
