package cn.arkmillion.agones;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/** Unchecked SDK failure retaining the original gRPC status code. */
public final class AgonesSdkException extends RuntimeException {
    private final Status.Code statusCode;

    public AgonesSdkException(String operation, Throwable cause) {
        super(operation + " failed: " + Status.fromThrowable(cause).getDescription(), cause);
        this.statusCode = Status.fromThrowable(cause).getCode();
    }

    public AgonesSdkException(String message) {
        super(message);
        this.statusCode = Status.Code.INVALID_ARGUMENT;
    }

    public Status.Code getStatusCode() { return statusCode; }

    public static RuntimeException wrap(String operation, Throwable cause) {
        if (cause instanceof AgonesSdkException) return (AgonesSdkException) cause;
        if (cause instanceof StatusRuntimeException) return new AgonesSdkException(operation, cause);
        return cause instanceof RuntimeException ? (RuntimeException) cause : new AgonesSdkException(operation, cause);
    }
}

