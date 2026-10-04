package cn.arkmillion.agones;

/** Lifecycle state of a GameServer watch. */
public enum WatchState {
  CONNECTING,
  ACTIVE,
  RETRY_WAIT,
  CLOSED
}
