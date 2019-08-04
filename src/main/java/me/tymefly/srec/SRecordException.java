package me.tymefly.srec;

import javax.annotation.Nonnull;


/**
 * Unchecked exception for SRecord reading an writing
 */
public class SRecordException extends RuntimeException {
    private static final long serialVersionUID = 0x01;


    /**
     * Constructor for a raw message
     * @param message       Human readable (raw) message
     */
    SRecordException(@Nonnull String message) {
        super(message);
    }


    /**
     * Constructor for a formatted message
     * @param message       formatted message string
     * @param args          formatting arguments
     * @see java.util.Formatter
     */
    SRecordException(@Nonnull String message, @Nonnull Object... args) {
        super(String.format(message, args));
    }


    /**
     * Constructor for a wrapped exception
     * @param message       Human readable (raw) message
     * @param cause         Wrapped exception
     */
    SRecordException(@Nonnull String message, @Nonnull Throwable cause) {
        super(message, cause);
    }
}

