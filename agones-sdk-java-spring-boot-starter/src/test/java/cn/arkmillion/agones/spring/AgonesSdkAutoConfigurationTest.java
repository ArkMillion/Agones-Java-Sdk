package cn.arkmillion.agones.spring;

import static org.assertj.core.api.Assertions.assertThat;

import cn.arkmillion.agones.AgonesSdk;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgonesSdkAutoConfigurationTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AgonesSdkAutoConfiguration.class))
          .withBean(
              ManagedChannel.class,
              () -> InProcessChannelBuilder.forName("starter-test").directExecutor().build())
          .withPropertyValues(
              "agones.sdk.health-enabled=false", "agones.sdk.shutdown-on-exit=false");

  @Test
  void createsSdkAndLifecycle() {
    context.run(
        value -> {
          assertThat(value).hasSingleBean(AgonesSdk.class);
          assertThat(value).hasSingleBean(AgonesSdkLifecycle.class);
        });
  }

  @Test
  void canBeDisabled() {
    context
        .withPropertyValues("agones.sdk.enabled=false")
        .run(value -> assertThat(value).doesNotHaveBean(AgonesSdk.class));
  }
}
