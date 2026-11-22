package com.notshahrad.spring_boot_base.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "base.websocket.enabled", havingValue = "true")
class DemoMessagePolicy implements WebSocketMessageBrokerConfigurer, ChannelInterceptor {
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(this);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) {
            return message;
        }
        boolean allowed = switch (headers.getCommand()) {
            case CONNECT, STOMP -> headers.getUser() != null;
            case SEND -> headers.getUser() != null && "/app/broadcast".equals(headers.getDestination());
            case SUBSCRIBE -> headers.getUser() != null && "/topic/broadcast".equals(headers.getDestination());
            case UNSUBSCRIBE, DISCONNECT -> true;
            default -> false;
        };
        if (!allowed) {
            throw new AccessDeniedException("WebSocket operation denied");
        }
        return message;
    }
}
