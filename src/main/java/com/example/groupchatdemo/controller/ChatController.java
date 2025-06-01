package com.example.groupchatdemo.controller;

import com.example.groupchatdemo.model.Message;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket消息控制器，处理来自客户端的聊天消息。
 * 它负责接收消息，并将消息路由到正确的目的地（广播或私聊）。
 * 同时，也管理在线用户列表。
 */
@Controller
public class ChatController {

    // 用于向客户端发送消息的Spring工具类
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    public ChatController(SimpMessagingTemplate messagingTemplate, SimpUserRegistry simpUserRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * 处理客户端发送的公共聊天消息。
     * 消息映射到 "/app/chat.sendMessage"，发送后将广播到 "/topic/public"。
     * @param chatMessage 客户端发送的聊天消息对象
     * @return 将转发给所有订阅者的消息对象
     */
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public") // 将消息广播到此主题
    public Message sendMessage(@Payload Message chatMessage) {
        // 简单地返回接收到的消息，Spring会自动将其广播到/topic/public
        // 可以在这里添加消息存储、敏感词过滤等逻辑
        System.out.println("收到公共消息: " + chatMessage);
        return chatMessage;
    }

    /**
     * 处理客户端发送的添加用户消息（用户加入聊天室）。
     * 消息映射到 "/app/chat.addUser"。
     * 当用户加入时，会话信息会被存储，并广播更新的用户列表。
     * @param chatMessage 客户端发送的包含用户名信息的Message对象
     * @param headerAccessor 用于访问会话头的对象，可以获取sessionId
     * @return 广播给所有订阅者的用户加入消息
     */
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public") // 将用户加入消息广播到此主题
    public Message addUser(@Payload Message chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        String username = chatMessage.getFrom();
        String sessionId = headerAccessor.getSessionId();

        // 将sessionId存储到会话属性中，以便在断开连接时获取
        // Principal的设置现在由UserInterceptor处理
        if (sessionId != null && username != null) {
            headerAccessor.getSessionAttributes().put("username", username);
        }

        System.out.println("用户 " + username + " 加入聊天室。SessionId: " + sessionId);
        // 广播用户列表更新现在由WebSocketEventListener处理
        // broadcastUserList(); // 移除此行

        // 1. 将当前的在线用户列表私聊发送给新加入的用户
        sendOnlineUsersToUser(username);

        // 2. 广播用户加入消息给所有订阅了/topic/public的客户端
        // 同时广播最新的用户列表给所有在线用户
        broadcastUserListFromRegistry();

        return new Message(username, null, "", false, "JOIN"); // command为JOIN，内容为空，表示用户加入
    }

    /**
     * 处理客户端发送的私聊消息。
     * 消息映射到 "/app/chat.sendPrivateMessage"。
     * @param privateMessage 客户端发送的私聊消息对象
     */
    @MessageMapping("/chat.sendPrivateMessage")
    public void sendPrivateMessage(@Payload Message privateMessage) {
        String toUser = privateMessage.getTo();
        String fromUser = privateMessage.getFrom();

        System.out.println("sendPrivateMessage: Attempting to send from " + fromUser + " to " + toUser);

        // 构建要发送的私聊消息
        Message messageToSend = new Message(
                fromUser,
                toUser,
                privateMessage.getContent(),
                true,
                "PRIVATE_CHAT"
        );

        // 使用messagingTemplate发送私聊消息给特定用户（使用用户名作为Principal名称）
        // 发送到 /user/{username}/queue/messages 目的地
        messagingTemplate.convertAndSendToUser(toUser, "/queue/messages", messageToSend);
        System.out.println("私聊消息从 " + fromUser + " 发送给 " + toUser + ": " + privateMessage.getContent());

        // 同时，将私聊消息回显给发送者，让发送者也能在自己的界面上看到已发送的私聊
        messagingTemplate.convertAndSendToUser(fromUser, "/queue/messages", messageToSend);

        // 移除手动判断用户是否在线的逻辑，让convertAndSendToUser自行处理（不在线会静默失败）
        // 客户端可以通过SERVER_INFO消息提示用户不在线，但现在由客户端自己判断或我们后续再添加此功能
    }

    /**
     * 发送当前在线用户列表给指定用户作为私聊消息。
     * 这用于当新用户连接时，向其显示当前在线的用户列表。
     * @param targetUsername 接收在线用户列表的目标用户名。
     */
    private void sendOnlineUsersToUser(String targetUsername) {
        Set<String> distinctUsernames = simpUserRegistry.getUsers().stream()
                .map(user -> user.getName())
                .collect(Collectors.toSet());
        String userListContent = String.join(",", distinctUsernames);
        Message userListMessage = new Message("Server", null, userListContent, false, "USER_LIST_UPDATE");

        // 通过私聊队列发送给目标用户
        messagingTemplate.convertAndSendToUser(targetUsername, "/queue/messages", userListMessage);
        System.out.println("ChatController: Sent user list update to " + targetUsername + ": " + userListContent);
    }

    /**
     * 从SimpUserRegistry获取在线用户列表并广播。
     */
    private void broadcastUserListFromRegistry() {
        Set<String> distinctUsernames = simpUserRegistry.getUsers().stream()
                .map(user -> user.getName())
                .collect(Collectors.toSet());
        String userListContent = String.join(",", distinctUsernames);
        Message userListMessage = new Message("Server", null, userListContent, false, "USER_LIST_UPDATE");
        messagingTemplate.convertAndSend("/topic/public", userListMessage);
        System.out.println("ChatController: Broadcast user list update to public: " + userListContent);
    }

    // 移除broadcastUserList方法，因为它现在由WebSocketEventListener处理
    // private void broadcastUserList() { ... }

    // 移除handleWebSocketDisconnectListener方法，因为它现在由WebSocketEventListener处理
    // @EventListener
    // public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) { ... }
}
