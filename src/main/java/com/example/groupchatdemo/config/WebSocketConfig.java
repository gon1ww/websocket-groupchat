package com.example.groupchatdemo.config;

import com.example.groupchatdemo.interceptor.UserInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置类，用于启用WebSocket消息代理。
 * 配置STOMP端点，消息代理，以及消息输入通道拦截器。
 */
@Configuration
@EnableWebSocketMessageBroker // 启用WebSocket消息处理
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final UserInterceptor userInterceptor;

    public WebSocketConfig(UserInterceptor userInterceptor) {
        this.userInterceptor = userInterceptor;
    }

    /**
     * 注册STOMP端点。
     * 客户端将使用此端点连接到WebSocket服务器。
     * @param registry STOMP端点注册表
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册一个WebSocket端点，客户端将通过"/ws"连接
        // withSockJS() 启用SockJS备用选项，以便在WebSocket不可用时使用。
        registry.addEndpoint("/ws").withSockJS();
    }

    /**
     * 配置消息代理。
     * 消息代理将消息从一个客户端路由到另一个客户端。
     * @param config 消息代理注册表
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 设置消息代理的前缀，当消息发送到这些前缀时，会路由到消息代理（如RabbitMQ, ActiveMQ或简单的内存代理）
        config.enableSimpleBroker("/topic", "/queue"); // /topic 用于公共广播，/queue 用于私聊
        // 设置应用程序目的地的前缀。所有发送到此前缀的消息都将被路由到带有@MessageMapping注解的方法
        config.setApplicationDestinationPrefixes("/app");
        // 设置用户目的地的前缀。用于点对点消息发送（私聊），通常与 SimpMessagingTemplate.convertAndSendToUser() 结合使用
        config.setUserDestinationPrefix("/user");
    }

    /**
     * 配置客户端入站通道拦截器。
     * @param registration 通道注册表
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(userInterceptor);
    }
}
