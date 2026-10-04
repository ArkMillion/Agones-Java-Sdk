package cn.arkmillion.agones.example;

import cn.arkmillion.agones.AgonesSdk;
import cn.arkmillion.agones.HealthPing;
import java.time.Duration;

/** Minimal lifecycle example intended for use with sdk-server --local or inside an Agones Pod. */
public final class SimpleGameServer {
  private SimpleGameServer() {}

  public static void main(String[] args) throws Exception {
    try (AgonesSdk sdk = AgonesSdk.builder().shutdownHook(true).build();
        HealthPing health = sdk.startHealthPing(Duration.ofSeconds(2))) {
      sdk.watchGameServer(
          gs -> System.out.println("GameServer state: " + gs.getStatus().getState()));
      sdk.ready();
      System.out.println("Server ready; press Enter to shut down");
      System.in.read();
      sdk.shutdown();
    }
  }
}
