package example.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = BaseWebSocketStarterTest.ConsumerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "base.websocket.enabled=true")
class BaseWebSocketStarterTest {

    @LocalServerPort
    int port;

    @Test
    void theConsumerAuthenticatesTheHandshakeWithoutDemoCode() throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        try {
            var url = "ws://localhost:" + port + "/ws";
            assertThatThrownBy(() -> client.connectAsync(url, new StompSessionHandlerAdapter() {})
                    .get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);

            var headers = new WebSocketHttpHeaders();
            headers.setBasicAuth("test-member", "test-password");
            var session = client.connectAsync(url, headers, new StompSessionHandlerAdapter() {})
                    .get(5, TimeUnit.SECONDS);
            try {
                assertThat(session.isConnected()).isTrue();
            } finally {
                session.disconnect();
            }
        } finally {
            client.stop();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
        @Bean
        SecurityFilterChain handshakeSecurity(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults()).build();
        }

        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(User.withUsername("test-member")
                    .password("{noop}test-password").roles("USER").build());
        }
    }
}
