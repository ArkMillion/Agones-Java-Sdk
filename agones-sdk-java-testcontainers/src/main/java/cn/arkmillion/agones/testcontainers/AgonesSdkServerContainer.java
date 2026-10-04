package cn.arkmillion.agones.testcontainers;

import cn.arkmillion.agones.AgonesSdk;
import java.nio.file.Path;
import org.testcontainers.utility.DockerImageName;

/**
 * @deprecated use {@link AgonesSdkContainer}.
 */
@Deprecated
public final class AgonesSdkServerContainer extends AgonesSdkContainer {
  public AgonesSdkServerContainer() {
    super();
  }

  public AgonesSdkServerContainer(DockerImageName image) {
    super(image);
  }

  @Override
  public AgonesSdkServerContainer withGameServerConfig(Path file) {
    super.withGameServerConfig(file);
    return this;
  }

  /**
   * @deprecated use {@link #createClient()}.
   */
  @Deprecated
  public AgonesSdk newSdk() {
    return createClient();
  }
}
