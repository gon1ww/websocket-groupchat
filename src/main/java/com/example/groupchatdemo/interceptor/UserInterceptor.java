package com.example.groupchatdemo.interceptor;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * 自定义UserInterceptor，用于在WebSocket连接时设置用户的Principal。
 * 这确保了Spring Security和SimpMessagingTemplate.convertAndSendToUser能够正确识别用户。
 */
@Component
public class UserInterceptor implements ChannelInterceptor {

    /**
     * 在消息发送到通道之前调用此方法。
     * 我们在这里拦截STOMP CONNECT消息，并从会话属性中设置用户的Principal。
     * @param message 要发送的消息
     * @param channel 消息将发送到的通道
     * @return 修改后的消息，或原始消息，如果未修改
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // 获取STOMP消息头访问器
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // 检查是否是CONNECT消息，即用户正在连接到WebSocket
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 从nativeHeaders中获取用户名，因为它是通过URL查询参数传递的
            String username = accessor.getFirstNativeHeader("username");

            // 如果用户名存在，则将其设置为此WebSocket会话的Principal
            if (username != null) {
                // 使用lambda表达式创建Principal，Spring将使用此名称来识别用户
                accessor.setUser(() -> username);
                System.out.println("UserInterceptor: Principal set for user: " + username + " from native headers.");
            } else {
                // 如果用户名不存在，可能是因为查询参数未提供
                System.out.println("UserInterceptor: Warning: Username not found in native headers for CONNECT command.");
            }
        }
        return message;
    }
} 