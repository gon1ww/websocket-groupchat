package com.example.groupchatdemo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Message类代表聊天室中的一条消息，可以用于群聊或私聊。
 * 实现Serializable接口，使得Message对象可以通过ObjectOutputStream在网络中传输，
 * 并通过ObjectInputStream进行反序列化，从而实现对象的跨进程通信。
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L; // 推荐添加serialVersionUID

    private String from;
    private String to; // null表示群聊，表示消息发送给谁
    private String content; // 消息的具体内容
    private boolean isPrivate; // 标记是否为私聊消息
    private String command; // 新增：消息的命令类型，例如 "CHAT", "LOGIN", "USER_LIST_UPDATE", "PRIVATE_CHAT"

    /**
     * 构造函数，用于创建不同类型的消息对象。
     * @param from 消息发送者用户名
     * @param to 消息接收者用户名（群聊时为null）
     * @param content 消息内容（例如聊天文本、用户列表JSON字符串等）
     * @param isPrivate 是否为私聊消息
     * @param command 消息命令类型（例如 "CHAT", "LOGIN", "USER_LIST_UPDATE", "PRIVATE_CHAT"）
     */
    public Message(String from, String to, String content, boolean isPrivate, String command) {
        this.from = from;
        this.to = to;
        this.content = content;
        this.isPrivate = isPrivate;
        this.command = command;
    }

    // Getter方法
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public String getContent() { return content; }

    @JsonProperty("isPrivate") // 确保JSON中字段名为isPrivate
    public boolean isPrivate() { return isPrivate; }
    public String getCommand() { return command; }

    // Setter方法（如果需要修改消息内容，虽然通常消息对象应该是不可变的）
    public void setFrom(String from) { this.from = from; }
    public void setTo(String to) { this.to = to; }
    public void setContent(String content) { this.content = content; }
    public void setPrivate(boolean aPrivate) { isPrivate = aPrivate; }
    public void setCommand(String command) { this.command = command; }

    @Override
    public String toString() {
        return "Message{" +
               "from='" + from + '\'' +
               ", to='" + (to != null ? to : "null") + '\'' +
               ", content='" + content + '\'' +
               ", isPrivate=" + isPrivate +
               ", command='" + command + '\'' +
               '}';
    }
} 