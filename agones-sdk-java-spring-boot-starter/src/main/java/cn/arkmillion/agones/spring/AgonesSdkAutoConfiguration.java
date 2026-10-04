package cn.arkmillion.agones.spring;

import cn.arkmillion.agones.AgonesSdk;
import cn.arkmillion.agones.RpcObserver;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates and manages an {@link AgonesSdk} when the core SDK is on the classpath. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(AgonesSdk.class)
@ConditionalOnProperty(prefix = "agones.sdk", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(AgonesSdkProperties.class)
public class AgonesSdkAutoConfiguration {
  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  public AgonesSdk agonesSdk(
      AgonesSdkProperties properties,
      ObjectProvider<RpcObserver> observer,
      ObjectProvider<ManagedChannel> channel) {
    AgonesSdk.Builder builder =
        AgonesSdk.builder().deadline(properties.getDeadline()).tls(properties.isTls());
    ManagedChannel selectedChannel = channel.getIfAvailable();
    if (selectedChannel != null) builder.channel(selectedChannel);
    else builder.address(properties.getHost(), properties.getPort());
    RpcObserver selected = observer.getIfAvailable();
    if (selected != null) builder.observer(selected);
    return builder.build();
  }

  @Bean
  @ConditionalOnMissingBean
  public AgonesSdkLifecycle agonesSdkLifecycle(AgonesSdk sdk, AgonesSdkProperties properties) {
    return new AgonesSdkLifecycle(sdk, properties);
  }
}
