package com.github.groundbreakingmc.gikymessage;

/**
 * Reports a template that cannot be compiled. Either the selected sprite key or a font key
 * is invalid, and its cause is preserved, or the template uses a feature that the Adventure
 * API in use cannot represent: shadow colors before 4.18, head and sprite objects before 4.25.
 * Other malformed syntax may be tolerated; this is not a general validation result.
 *
 * @see Text#of(String)
 * @since 1.0
 */
public final class TextFormatException extends IllegalArgumentException {

    /**
     * Creates a format exception.
     *
     * @param message the detail message, or {@code null}
     * @param cause   the underlying failure, or {@code null}
     * @since 1.0
     */
    public TextFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
