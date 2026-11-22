package com.notshahrad.base.websocket;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "base.websocket.enabled", havingValue = "true")
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(BaseWebSocketConfiguration.Settings.class)
public class BaseWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    @ConfigurationProperties("base.websocket")
    public record Settings(List<String> allowedOrigins) {
        public Settings {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
            if (allowedOrigins.contains("*")) {
                throw new IllegalArgumentException("Specify individual WebSocket origins");
            }
        }
    }

    private final Settings settings;

    public BaseWebSocketConfiguration(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint("/ws").setAllowedOrigins(settings.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }
}
