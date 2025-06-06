# 多用户在线聊天系统

这是一个基于Spring Boot和WebSocket实现的多用户在线聊天系统。系统包含一个服务器端和多个客户端，支持多用户同时在线聊天，并提供公开消息和私聊消息功能，以及查看在线用户列表。

## 系统设计与功能

- **服务器端**: 负责接收客户端连接、转发消息、管理在线用户列表。
- **客户端**: 可以连接服务器、发送公开消息、发送私聊消息、查看在线用户列表。

## 核心技术点

### 1. Java网络编程 (WebSocket)

尽管传统Java网络编程使用`Socket`和`ServerSocket`，本项目利用Spring Boot的WebSocket支持，简化了网络通信的复杂性。WebSocket提供了全双工的通信通道，允许服务器和客户端之间进行双向实时数据传输。

**代码示例 (ChatController.java):**

```java
// ... existing code ...
import org.springframework.messaging.handler.annotation.MessageMapping;       // 导入用于处理WebSocket消息的注解
import org.springframework.messaging.handler.annotation.Payload;             // 导入用于提取消息负载的注解
import org.springframework.messaging.handler.annotation.SendTo;               // 导入用于指定消息广播目的地的注解
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;      // 导入用于访问WebSocket会话头的类
import org.springframework.messaging.simp.SimpMessagingTemplate;          // 导入用于向客户端发送消息的Spring工具类
import org.springframework.messaging.simp.user.SimpUserRegistry;           // 导入用于管理在线用户的注册表
import org.springframework.stereotype.Controller;                            // 导入Spring的控制器注解
// ... existing code ...

/**
 * WebSocket消息控制器，处理来自客户端的聊天消息。
 * 它负责接收消息，并将消息路由到正确的目的地（广播或私聊）。
 * 同时，也管理在线用户列表。
 */
@Controller // 标记这是一个Spring MVC控制器，处理HTTP请求以及WebSocket消息
public class ChatController {

    // SimpMessagingTemplate用于向客户端发送消息，无论是广播还是私聊
    private final SimpMessagingTemplate messagingTemplate;
    // SimpUserRegistry用于获取当前在线用户的信息
    private final SimpUserRegistry simpUserRegistry;

    // 构造函数注入所需的依赖
    public ChatController(SimpMessagingTemplate messagingTemplate, SimpUserRegistry simpUserRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * 处理客户端发送的公共聊天消息。
     * 当客户端向 "/app/chat.sendMessage" 发送消息时，此方法会被调用。
     * 消息处理后，通过 @SendTo 注解将其广播到 "/topic/public" 主题，所有订阅此主题的客户端都会收到消息。
     * @param chatMessage 客户端发送的聊天消息对象，通过 @Payload 注解绑定消息体
     * @return 返回的消息对象会被Spring自动转换为JSON并发送给客户端
     */
    @MessageMapping("/chat.sendMessage") // 映射客户端发送到此路径的消息
    @SendTo("/topic/public") // 将处理后的消息广播到此主题
    public Message sendMessage(@Payload Message chatMessage) {
        // 打印收到的公共消息内容
        System.out.println("收到公共消息: " + chatMessage);
        // 简单地返回接收到的消息，Spring会自动将其广播到/topic/public
        // 可以在这里添加消息存储、敏感词过滤等业务逻辑
        return chatMessage;
    }

    /**
     * 处理客户端发送的添加用户消息（用户加入聊天室）。
     * 当客户端向 "/app/chat.addUser" 发送消息时，此方法会被调用。
     * 方法会记录用户的会话信息，并执行以下操作：
     * 1. 将当前的在线用户列表私聊发送给新加入的用户。
     * 2. 广播用户加入消息给所有订阅了 "/topic/public" 的客户端，并同时广播最新的用户列表。
     * @param chatMessage 客户端发送的包含用户名信息的Message对象
     * @param headerAccessor 用于访问WebSocket会话头的对象，可以获取sessionId等会话信息
     * @return 广播给所有订阅者的用户加入消息
     */
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public") // 将用户加入消息广播到此主题
    public Message addUser(@Payload Message chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        String username = chatMessage.getFrom(); // 获取加入用户的用户名
        String sessionId = headerAccessor.getSessionId(); // 获取用户的会话ID

        // 将用户名存储到会话属性中，以便在用户断开连接时进行清理
        if (sessionId != null && username != null) {
            headerAccessor.getSessionAttributes().put("username", username);
        }

        System.out.println("用户 " + username + " 加入聊天室。SessionId: " + sessionId);
        
        // 1. 将当前的在线用户列表私聊发送给新加入的用户
        sendOnlineUsersToUser(username);

        // 2. 广播用户加入消息给所有订阅了/topic/public的客户端
        // 同时广播最新的用户列表给所有在线用户
        broadcastUserListFromRegistry();

        // 返回一个命令为"JOIN"的消息，通知所有客户端有新用户加入
        return new Message(username, null, "", false, "JOIN");
    }

    /**
     * 处理客户端发送的私聊消息。
     * 当客户端向 "/app/chat.sendPrivateMessage" 发送消息时，此方法会被调用。
     * 消息会被转发给指定的目标用户，同时也会回显给发送者，确保发送者也能看到自己发出的私聊消息。
     * @param privateMessage 客户端发送的私聊消息对象
     */
    @MessageMapping("/chat.sendPrivateMessage") // 映射客户端发送到此路径的私聊消息
    public void sendPrivateMessage(@Payload Message privateMessage) {
        String toUser = privateMessage.getTo();   // 私聊消息的目标用户
        String fromUser = privateMessage.getFrom(); // 私聊消息的发送者
        
        System.out.println("sendPrivateMessage: Attempting to send from " + fromUser + " to " + toUser);

        // 构建要发送的私聊消息对象
        Message messageToSend = new Message(
            fromUser,
            toUser,
            privateMessage.getContent(),
            true, // 标记为私聊消息
            "PRIVATE_CHAT" // 命令类型为私聊
        );
        
        // 使用messagingTemplate发送私聊消息给特定用户。
        // 消息将发送到 "/user/{username}/queue/messages" 目的地，只有目标用户能接收到。
        messagingTemplate.convertAndSendToUser(toUser, "/queue/messages", messageToSend);
        System.out.println("私聊消息从 " + fromUser + " 发送给 " + toUser + ": " + privateMessage.getContent());
        
        // 同时，将私聊消息回显给发送者，让发送者也能在自己的界面上看到已发送的私聊
        messagingTemplate.convertAndSendToUser(fromUser, "/queue/messages", messageToSend);

        // Spring的convertAndSendToUser方法会自动处理用户是否在线。如果用户不在线，消息会静默失败。
    }

    /**
     * 发送当前在线用户列表给指定用户作为私聊消息。
     * 主要用于当新用户连接时，向其显示当前在线的用户列表。
     * @param targetUsername 接收在线用户列表的目标用户名。
     */
    private void sendOnlineUsersToUser(String targetUsername) {
        // 从SimpUserRegistry获取所有在线用户的会话，并提取出唯一的用户名
        Set<String> distinctUsernames = simpUserRegistry.getUsers().stream()
                .map(user -> user.getName()) // 获取每个在线用户的用户名
                .collect(Collectors.toSet()); // 收集为Set，自动去重

        String userListContent = String.join(",", distinctUsernames); // 将用户名用逗号连接成字符串
        
        // 创建一个包含用户列表的特殊消息，命令类型为"USER_LIST_UPDATE"
        Message userListMessage = new Message("Server", null, userListContent, false, "USER_LIST_UPDATE");

        // 通过私聊队列发送给目标用户，使其能够立即接收到当前在线用户列表
        messagingTemplate.convertAndSendToUser(targetUsername, "/queue/messages", userListMessage);
        System.out.println("ChatController: Sent user list update to " + targetUsername + ": " + userListContent);
    }

    /**
     * 从SimpUserRegistry获取在线用户列表并广播给所有在线用户。
     * 当用户加入或离开时，此方法会被调用以更新所有客户端的用户列表显示。
     */
    private void broadcastUserListFromRegistry() {
        // 从SimpUserRegistry获取所有在线用户的会话，并提取出唯一的用户名
        Set<String> distinctUsernames = simpUserRegistry.getUsers().stream()
                .map(user -> user.getName()) // 获取每个在线用户的用户名
                .collect(Collectors.toSet()); // 收集为Set，自动去重

        String userListContent = String.join(",", distinctUsernames); // 将用户名用逗号连接成字符串
        
        // 创建一个包含用户列表的特殊消息，命令类型为"USER_LIST_UPDATE"
        Message userListMessage = new Message("Server", null, userListContent, false, "USER_LIST_UPDATE");
        
        // 将用户列表更新消息广播到 "/topic/public" 主题，所有订阅此主题的客户端都会收到更新
        messagingTemplate.convertAndSend("/topic/public", userListMessage);
        System.out.println("ChatController: Broadcast user list update to public: " + userListContent);
    }
}
```

### 2. 多线程 (服务器端并发处理)

Spring Boot WebSocket框架底层会利用线程池来处理并发的客户端连接和消息。每个客户端的连接和消息处理请求都会由线程池中的一个线程来处理，从而实现多用户同时在线的并发处理能力，而无需开发者手动管理线程的创建和销毁。这种方式极大地简化了并发编程的复杂性，开发者可以专注于业务逻辑。

### 3. 线程同步与通信 (共享资源管理)

服务器端需要管理在线用户列表等共享资源，例如在用户加入或离开时更新列表，以及在发送消息时查找目标用户。Spring的`SimpMessagingTemplate`和`SimpUserRegistry`提供了高级抽象来处理这些共享资源，确保在多线程环境下进行用户列表的更新和消息的路由时是线程安全的。

**代码示例 (ChatController.java):**

```java
// ... existing code ...
import java.util.Set;               // 导入Set接口，用于存储不重复的用户名
import java.util.stream.Collectors; // 导入Collectors类，用于Stream API的数据收集
// ... existing code ...

// SimpMessagingTemplate用于向客户端发送消息，已在构造函数中注入，确保线程安全的消息发送
private final SimpMessagingTemplate messagingTemplate; 
// SimpUserRegistry用于管理和查询在线用户，已在构造函数中注入，确保线程安全的用户列表管理
private final SimpUserRegistry simpUserRegistry;     

// 构造函数：Spring会自动注入这两个依赖，它们都是线程安全的单例bean
public ChatController(SimpMessagingTemplate messagingTemplate, SimpUserRegistry simpUserRegistry) {
    this.messagingTemplate = messagingTemplate;
    this.simpUserRegistry = simpUserRegistry;
}

/**
 * 从SimpUserRegistry获取在线用户列表并广播。
 * 这个方法展示了如何通过simpUserRegistry线程安全地访问和更新在线用户列表，
 * 然后通过messagingTemplate线程安全地广播更新。
 */
private void broadcastUserListFromRegistry() {
    // 获取所有在线用户的名称集合。SimpUserRegistry内部处理了并发访问的线程安全。
    Set<String> distinctUsernames = simpUserRegistry.getUsers().stream()
            .map(user -> user.getName()) // 获取每个在线用户的用户名 (Principal name)
            .collect(Collectors.toSet()); // 收集为Set，自动去重，确保用户列表唯一性

    String userListContent = String.join(",", distinctUsernames); // 将用户名用逗号连接成字符串
    
    // 创建一个包含用户列表的特殊消息对象
    Message userListMessage = new Message("Server", null, userListContent, false, "USER_LIST_UPDATE");
    
    // 将用户列表更新消息广播到/topic/public主题。messagingTemplate内部处理了消息发送的线程安全。
    messagingTemplate.convertAndSend("/topic/public", userListMessage);
    System.out.println("ChatController: Broadcast user list update to public: " + userListContent);
}
```

### 4. I/O流操作

在Spring Boot WebSocket中，底层的I/O流操作（如数据的读取和写入）由框架自动管理和抽象。开发者无需直接处理`InputStream`或`OutputStream`，而是通过`@MessageMapping`、`@Payload`等注解以及`SimpMessagingTemplate`来发送和接收结构化的消息对象。这种高级抽象使得开发者可以专注于应用层的数据交互，而无需关心底层的字节流处理。

### 5. 对象序列化 (消息对象传输)

为了在网络间传输复杂对象（如聊天消息），`Message`类实现了`Serializable`接口。在Spring WebSocket中，消息通常以JSON格式通过网络传输，Spring框架内部使用Jackson库进行对象的序列化和反序列化。`Serializable`接口的实现提供了一种备选的序列化机制，但本项目主要依赖于JSON，Jackson库提供了更高效和灵活的JSON序列化/反序列化能力。

**代码示例 (Message.java):**

```java
package com.example.groupchatdemo.model;

import java.io.Serializable; // 导入Serializable接口，标记此类的对象可以被序列化
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson库的注解，用于JSON序列化/反序列化时指定字段名

/**
 * Message类代表聊天室中的一条消息，可以用于群聊或私聊。
 * 实现Serializable接口，使得Message对象可以通过ObjectOutputStream在网络中传输（尽管本项目主要使用JSON），
 * 并通过ObjectInputStream进行反序列化，从而实现对象的跨进程通信。
 * 在基于Spring和Jackson的WebSocket应用中，JSON序列化是主要的传输方式，
 * 但Serializable接口的实现增加了Java原生序列化的兼容性。
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L; // 推荐添加serialVersionUID，用于版本控制，确保序列化和反序列化兼容性

    private String from;       // 消息发送者用户名
    private String to;         // 消息接收者用户名 (如果为null，则表示为群聊消息)
    private String content;    // 消息的具体内容 (例如聊天文本、用户列表的JSON字符串等)
    private boolean isPrivate; // 标记是否为私聊消息 (true为私聊，false为群聊)
    private String command;    // 消息的命令类型 (例如 "CHAT": 普通聊天消息, "LOGIN": 用户登录通知, "USER_LIST_UPDATE": 用户列表更新, "PRIVATE_CHAT": 私聊消息)

    /**
     * 构造函数，用于创建不同类型的消息对象。
     * @param from 消息发送者用户名
     * @param to 消息接收者用户名（群聊时为null）
     * @param content 消息内容
     * @param isPrivate 是否为私聊消息
     * @param command 消息命令类型
     */
    public Message(String from, String to, String content, boolean isPrivate, String command) {
        this.from = from;
        this.to = to;
        this.content = content;
        this.isPrivate = isPrivate;
        this.command = command;
    }

    // Getter 方法：提供对消息属性的只读访问
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getContent() { return content; }

    @JsonProperty("isPrivate") // 确保JSON中字段名为"isPrivate" (通常getter方法名如果是isX，会自动映射为X，但这里显式指定)
    public boolean isPrivate() { return isPrivate; }
    public String getCommand() { return command; }

    // Setter 方法：提供对消息属性的写入访问 (尽管消息对象通常设计为不可变的)
    public void setFrom(String from) { this.from = from; }
    public void setTo(String to) { this.to = to; }
    public void setContent(String content) { this.content = content; }
    public void setPrivate(boolean aPrivate) { isPrivate = aPrivate; }
    public void setCommand(String command) { this.command = command; }

    /**
     * 重写toString方法，方便打印消息对象时查看其详细内容。
     * @return 消息对象的字符串表示
     */
    @Override
    public String toString() {
        return "Message{" +
               "from='" + from + ''' +
               ", to='" + (to != null ? to : "null") + ''' +
               ", content='" + content + ''' +
               ", isPrivate=" + isPrivate +
               ", command='" + command + ''' +
               '}';
    }
} 