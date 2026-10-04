package cn.arkmillion.agones.testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AgonesSdkServerContainerTest {
  @Test
  void pinsTheCompatibleAgonesImage() {
    AgonesSdkContainer container = new AgonesSdkContainer();
    assertEquals(AgonesSdkContainer.DEFAULT_IMAGE, container.getConfiguredImageName());
  }
}
