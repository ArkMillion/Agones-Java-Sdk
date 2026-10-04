package cn.arkmillion.agones.testcontainers;

import cn.arkmillion.agones.AgonesSdk;
import java.nio.file.Path;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Testcontainers wrapper for the real Agones SDK Server in local mode. */
public class AgonesSdkContainer extends GenericContainer<AgonesSdkContainer> {
  public static final String DEFAULT_IMAGE =
      "us-docker.pkg.dev/agones-images/release/agones-sdk:1.61.0";
  public static final int GRPC_PORT = 9357;

  public AgonesSdkContainer() {
    this(DockerImageName.parse(DEFAULT_IMAGE));
  }

  public AgonesSdkContainer(DockerImageName image) {
    super(image);
    withExposedPorts(GRPC_PORT);
    withCommand("--local", "--address", "0.0.0.0");
    waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));
  }

  /** Uses a custom GameServer YAML that defines test Counters and Lists. */
  public AgonesSdkContainer withGameServerConfig(Path file) {
    withCopyFileToContainer(MountableFile.forHostPath(file), "/tmp/gameserver.yaml");
    withCommand("--local", "--address", "0.0.0.0", "-f", "/tmp/gameserver.yaml");
    return self();
  }

  public String getGrpcHost() {
    return getHost();
  }

  public int getGrpcPort() {
    return getMappedPort(GRPC_PORT);
  }

  /** Returns the configured image without triggering Docker environment discovery. */
  public String getConfiguredImageName() {
    return DEFAULT_IMAGE;
  }

  public AgonesSdk createClient() {
    return AgonesSdk.builder().address(getGrpcHost(), getGrpcPort()).build();
  }
}
