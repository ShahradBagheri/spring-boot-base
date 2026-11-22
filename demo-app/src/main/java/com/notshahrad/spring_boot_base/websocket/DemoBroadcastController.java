package com.notshahrad.spring_boot_base.websocket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@ConditionalOnProperty(name = "base.websocket.enabled", havingValue = "true")
public class DemoBroadcastController {
    @MessageMapping("/broadcast")
    @SendTo("/topic/broadcast")
    public String broadcast(String text) {
        return text;
    }
}
