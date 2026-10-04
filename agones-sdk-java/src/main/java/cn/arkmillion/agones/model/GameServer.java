package cn.arkmillion.agones.model;

import java.util.Objects;

/** Immutable public representation of the fields exposed by the Agones SDK GameServer proto. */
public final class GameServer {
  private final ObjectMeta objectMeta;
  private final GameServerSpec spec;
  private final GameServerStatus status;

  public GameServer(ObjectMeta objectMeta, GameServerSpec spec, GameServerStatus status) {
    this.objectMeta = Objects.requireNonNull(objectMeta, "objectMeta");
    this.spec = Objects.requireNonNull(spec, "spec");
    this.status = Objects.requireNonNull(status, "status");
  }

  public ObjectMeta getObjectMeta() {
    return objectMeta;
  }

  public GameServerSpec getSpec() {
    return spec;
  }

  public GameServerStatus getStatus() {
    return status;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof GameServer
            && objectMeta.equals(((GameServer) other).objectMeta)
            && spec.equals(((GameServer) other).spec)
            && status.equals(((GameServer) other).status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(objectMeta, spec, status);
  }

  @Override
  public String toString() {
    return "GameServer{name='" + objectMeta.getName() + "', state='" + status.getState() + "'}";
  }
}
