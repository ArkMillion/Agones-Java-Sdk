package cn.arkmillion.agones;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/** Unchecked SDK failure retaining the original gRPC status code. */
public final class AgonesSdkException extends RuntimeException {
  private final Status.Code statusCode;
  private final String operation;

  public AgonesSdkException(String operation, Throwable cause) {
    super(message(operation, cause), cause);
    this.statusCode = Status.fromThrowable(cause).getCode();
    this.operation = operation;
  }

  public AgonesSdkException(String message) {
    super(message);
    this.statusCode = Status.Code.INVALID_ARGUMENT;
    this.operation = null;
  }

  public Status.Code getStatusCode() {
    return statusCode;
  }

  public String getOperation() {
    return operation;
  }

  public static RuntimeException wrap(String operation, Throwable cause) {
    if (cause instanceof AgonesSdkException) return (AgonesSdkException) cause;
    if (cause instanceof StatusRuntimeException) return new AgonesSdkException(operation, cause);
    return cause instanceof RuntimeException
        ? (RuntimeException) cause
        : new AgonesSdkException(operation, cause);
  }

  private static String message(String operation, Throwable cause) {
    Status status = Status.fromThrowable(cause);
    String description = status.getDescription();
    return operation
        + " failed ["
        + status.getCode()
        + "]"
        + (description == null || description.isEmpty() ? "" : ": " + description);
  }
}
