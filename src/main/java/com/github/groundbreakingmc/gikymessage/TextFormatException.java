package com.github.groundbreakingmc.gikymessage;

/**
 * Reports an invalid sprite key during template compilation, preserving its cause.
 * Other malformed syntax may be tolerated; this is not a general validation result.
 *
 * @see Text#of(String)
 * @since 1.0.0
 */
public final class TextFormatException extends IllegalArgumentException {

    /**
     * Creates a format exception.
     *
     * @param message the detail message, or {@code null}
     * @param cause   the underlying failure, or {@code null}
     * @since 1.0.0
     */
    public TextFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
