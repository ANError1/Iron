package org.embeddedt.embeddium.opticore.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging facade for the Opticore package.
 *
 * <p>The exception-aware overloads exist because SLF4J only treats a trailing {@link Throwable} as a
 * stack trace when it is not consumed by a placeholder. Passing an exception as the first vararg to
 * a single-placeholder message would print the exception's {@code toString} in place of the value
 * instead of logging its stack, which is a common and silent mistake.
 */
public final class OpticoreLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("Opticore");

    private OpticoreLog() {}

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }

    /** Logs a warning with its stack trace. */
    public static void warn(String message, Throwable cause) {
        LOGGER.warn(message, cause);
    }

    /** Logs a warning with a stack trace and one formatted argument. */
    public static void warn(String message, Throwable cause, Object arg) {
        LOGGER.warn(message, arg, cause);
    }

    /** Logs an error with its stack trace. */
    public static void error(String message, Throwable cause) {
        LOGGER.error(message, cause);
    }

    /** Logs an error with a stack trace and one formatted argument. */
    public static void error(String message, Throwable cause, Object arg) {
        LOGGER.error(message, arg, cause);
    }

    public static boolean isDebugEnabled() {
        return LOGGER.isDebugEnabled();
    }
}
