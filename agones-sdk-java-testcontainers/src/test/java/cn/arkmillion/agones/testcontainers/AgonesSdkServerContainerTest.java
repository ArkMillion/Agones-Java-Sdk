package cn.arkmillion.agones.testcontainers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AgonesSdkServerContainerTest {
    @Test void pinsTheCompatibleAgonesImage() {
        AgonesSdkServerContainer container = new AgonesSdkServerContainer();
        assertEquals(AgonesSdkServerContainer.DEFAULT_IMAGE, container.getConfiguredImageName());
    }
}
