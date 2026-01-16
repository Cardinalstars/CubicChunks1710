package com.cardinalstar.cubicchunks.server.chunkio;

public class LoadFailureException extends RuntimeException {

    public LoadFailureException(String message) {
        super(message);
    }

    public LoadFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public LoadFailureException(Throwable cause) {
        super(cause);
    }

    public LoadFailureException(String message, Throwable cause, boolean enableSuppression,
        boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
