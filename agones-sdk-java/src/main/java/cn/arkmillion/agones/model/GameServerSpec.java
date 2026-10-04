package cn.arkmillion.agones.model;

import java.util.Objects;

/** Immutable GameServer specification fields exposed by the SDK. */
public final class GameServerSpec {
  private final HealthConfiguration health;

  public GameServerSpec(HealthConfiguration health) {
    this.health = Objects.requireNonNull(health, "health");
  }

  public HealthConfiguration getHealth() {
    return health;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof GameServerSpec && health.equals(((GameServerSpec) other).health);
  }

  @Override
  public int hashCode() {
    return health.hashCode();
  }

  @Override
  public String toString() {
    return "GameServerSpec{health=" + health + "}";
  }
}
