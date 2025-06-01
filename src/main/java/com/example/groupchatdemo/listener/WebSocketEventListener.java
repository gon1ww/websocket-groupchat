package com.example.groupchatdemo.listener;

import com.example.groupchatdemo.model.Message;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * WebSocket事件监听器，用于处理用户连接和断开事件，并管理在线用户状态。
 * 主要用于调试SimpUserRegistry的Linter报错，因为在ChatController注入SimpUserRegistry会导致Linter报错。
 */
@Component
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    public WebSocketEventListener(SimpMessagingTemplate messagingTemplate, SimpUserRegistry simpUserRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * 监听WebSocket会话连接事件。
     * 当客户端成功连接到WebSocket时，Spring会自动触发此事件。
     * @param event SessionConnectedEvent事件对象，包含连接的会话信息
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String username = null;

        // 优先从Principal中获取用户名 (UserInterceptor会设置它)
        if (event.getUser() != null) {
            username = event.getUser().getName();
        } else if (headerAccessor.getSessionAttributes() != null) {
            // 如果Principal未设置，则尝试从会话属性中获取
            username = (String) headerAccessor.getSessionAttributes().get("username");
        }

        String sessionId = headerAccessor.getSessionId();

        System.out.println("WebSocketEventListener: User connected: " + (username != null ? username : "UNKNOWN") + ", SessionId: " + sessionId);
        // 调试：打印SimpUserRegistry中的用户信息
        printSimpUserRegistryContent("after connect");

        // 广播用户列表更新现在由ChatController.addUser通过私聊发送给新用户，并广播给所有用户
        // broadcastUserListFromRegistry(); // 移除此行
    }

    /**
     * 监听WebSocket会话断开连接事件，移除离线用户并广播更新的用户列表。
     * 当客户端断开连接时，Spring会自动触发此事件。
     * @param event SessionDisconnectEvent事件对象，包含断开连接的会话信息
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String username = null;

        // 优先从Principal中获取用户名
        if (event.getUser() != null) {
            username = event.getUser().getName();
        } else if (headerAccessor.getSessionAttributes() != null) {
            // 如果Principal未设置，则尝试从会话属性中获取
            username = (String) headerAccessor.getSessionAttributes().get("username");
        }

        String sessionId = headerAccessor.getSessionId();

        System.out.println("WebSocketEventListener: User disconnected: " + (username != null ? username : "UNKNOWN") + ", SessionId: " + sessionId);

        if (username != null && sessionId != null) {
            // 从SimpUserRegistry中移除用户是Spring自动处理的
            // 我们只需要广播更新的用户列表
            // 调试：打印SimpUserRegistry中的用户信息
            printSimpUserRegistryContent("after disconnect (before broadcast)");

            // 广播用户列表更新
            // broadcastUserListFromRegistry(); // 移除此行，因为ChatController会处理用户列表的广播
            // 替代：在ChatController.java中处理用户列表的广播，这里不再重复处理。
            // 我们仍然可以触发一个公共消息，通知用户离开了，但这与用户列表更新是两个不同的事情。
            // 目前保持现状，依赖ChatController来处理所有用户列表的广播。
        } else {
            if (username == null) {
                System.out.println("WebSocketEventListener: Warning: Disconnect event for sessionId: " + sessionId + " but username or principal not found.");
            }
            if (sessionId == null) {
                System.out.println("WebSocketEventListener: Error: Disconnect event occurred, but sessionId is null.");
            }
        }
        printSimpUserRegistryContent("after disconnect (final)");
    }

    /**
     * 从SimpUserRegistry获取在线用户列表并广播。
     * **此方法已被移动到ChatController.java中，此处不再需要。**
     */
    // private void broadcastUserListFromRegistry() { ... }

    /**
     * 调试方法：打印SimpUserRegistry中的用户及其会话ID。
     * @param stage 当前调试阶段描述
     */
    private void printSimpUserRegistryContent(String stage) {
        System.out.println("Debug: SimpUserRegistry content " + stage + ":");
        if (simpUserRegistry.getUsers().isEmpty()) {
            System.out.println("  (No users registered in SimpUserRegistry)");
        } else {
            simpUserRegistry.getUsers().forEach(user -> {
                System.out.println("  User: " + user.getName() + " (Session IDs: " +
                        user.getSessions().stream().map(session -> session.getId()).collect(Collectors.joining(",")) + ")");
            });
        }
    }
}
