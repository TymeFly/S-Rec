package me.tymefly.srec;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;


/**
 * Enumeration of SRecord types.
 */
public enum SRecord {
    INVALID(-1),
    HEADER(0),
    DATA_16(1),
    DATA_24(2),
    DATA_32(3),
    RESERVED(4),
    COUNT_16(5),
    COUNT_24(6),
    START_ADDRESS_32(7),
    START_ADDRESS_24(8),
    START_ADDRESS_16(9);

    private static final Map<Integer, SRecord> FROM_TYPE =
        Stream.of(values())
            .collect(Collectors.toMap(SRecord::getType, e -> e));

    private final int type;

    SRecord(int type) {
        this.type = type;
    }


    /**
     * Returns the numeric value associated with this SRecord
     * @return the numeric value associated with this SRecord
     */
    public int getType() {
        return type;
    }


    /**
     * Look up an SRecord by its type.
     * {@link #INVALID} is returned if {@code type} does not match a valid SRecord
     * @param type      Required type
     * @return          An SRecord type
     */
    @Nonnull
    public static SRecord fromType(int type) {
        SRecord result = FROM_TYPE.get(type);

        return (result == null ? SRecord.INVALID : result);
    }
}
