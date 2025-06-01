package com.example.groupchatdemo.client;

import java.io.*;
import java.net.Socket;
import com.example.groupchatdemo.model.Message;
import java.util.Scanner;

/**
 * ChatClient类是聊天室客户端的主入口点（命令行版本）。
 * 它负责连接服务器，发送和接收聊天消息，并在控制台显示交互。
 * 客户端使用对象流进行网络通信，实现消息对象的序列化传输。
 */
public class ChatClient {
    private Socket socket;
    // 用于向服务器发送序列化的Java对象（Message）
    private ObjectOutputStream objOut;
    // 用于从服务器接收序列化的Java对象（Message）
    private ObjectInputStream objIn;
    private String username;

    /**
     * 构造函数，初始化客户端用户名。
     * @param username 客户端将使用的用户名
     */
    public ChatClient(String username) {
        this.username = username;
    }

    /**
     * 连接到聊天服务器并初始化输入输出流。
     * 连接成功后，会发送一个登录消息并启动一个单独的线程来持续接收服务器消息。
     * @param host 服务器IP地址或主机名
     * @param port 服务器端口号
     * @throws IOException 如果发生I/O错误
     */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        System.out.println("尝试连接到服务器: " + host + ":" + port);
        // 获取原始输出流并包装成ObjectOutputStream，用于发送Message对象
        objOut = new ObjectOutputStream(socket.getOutputStream());
        // 获取原始输入流并包装成ObjectInputStream，用于接收Message对象
        // 注意：ObjectInputStream的创建可能会阻塞，直到ObjectOutputStream的头部信息被写入
        objIn = new ObjectInputStream(socket.getInputStream());

        // 发送登录消息给服务器，告知服务器自己的用户名和命令类型为"LOGIN"
        objOut.writeObject(new Message(username, null, "", false, "LOGIN"));
        objOut.flush(); // 确保消息立即发送
        System.out.println("已连接到服务器，并发送用户名: " + username);

        // 启动一个新线程来专门接收消息，避免阻塞主线程（用户输入）
        new Thread(this::receiveMessages, "MessageReceiverThread").start();
    }

    /**
     * 私有方法，在新线程中持续接收来自服务器的消息。
     * 根据消息的命令类型进行不同处理（例如显示聊天消息、更新用户列表等）。
     */
    private void receiveMessages() {
        try {
            while (true) {
                // 从输入流读取并反序列化Message对象
                Message msg = (Message) objIn.readObject();
                if (msg != null) {
                    // 根据消息的命令类型进行处理
                    switch (msg.getCommand()) {
                        case "CHAT": // 普通聊天消息
                            System.out.println("[" + msg.getFrom() + "]: " + msg.getContent());
                            break;
                        case "PRIVATE_CHAT": // 私聊消息
                            System.out.println("[私聊] " + msg.getFrom() + " 对你说: " + msg.getContent());
                            break;
                        case "USER_LIST_UPDATE": // 用户列表更新消息
                            System.out.println("[系统] 在线用户: " + msg.getContent());
                            break;
                        case "SERVER_INFO": // 服务器信息或错误提示
                            System.out.println("[服务器] " + msg.getContent());
                            break;
                        default:
                            System.out.println("[未知消息] " + msg.getFrom() + ": " + msg.getContent() + " (命令: " + msg.getCommand() + ")");
                            break;
                    }
                }
            }
        } catch (EOFException e) {
            // 服务器正常关闭或连接断开
            System.out.println("[系统] 服务器连接已关闭或断开。");
        } catch (IOException | ClassNotFoundException e) {
            // 捕获其他I/O异常或类找不到异常
            System.err.println("[系统] 接收消息时发生错误: " + e.getMessage());
            // e.printStackTrace(); // 调试时可以取消注释查看详细堆栈
        } finally {
            // 确保在连接断开时关闭资源
            try {
                if (objIn != null) objIn.close();
                if (objOut != null) objOut.close();
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
                // 忽略关闭资源时可能发生的异常
            }
        }
    }

    /**
     * 向服务器发送消息。
     * 将Message对象序列化并写入输出流。
     * @param msg 要发送的Message对象
     */
    public void sendMessage(Message msg) {
        try {
            // 将Message对象写入输出流，进行序列化
            objOut.writeObject(msg);
            objOut.flush(); // 立即清空缓冲区，确保消息发送
        } catch (IOException e) {
            System.err.println("[系统] 发送消息失败: " + e.getMessage());
            // e.printStackTrace();
        }
    }

    /**
     * 关闭客户端的Socket连接。
     * @throws IOException 如果关闭连接时发生I/O错误
     */
    public void close() throws IOException {
        // 在finally块中已处理，此处可移除或保留用于特定场景
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * 客户端程序的入口点。
     * 允许用户输入用户名，然后连接到服务器并开始发送/接收消息。
     * @param args 命令行参数 (未使用)
     * @throws IOException 如果发生I/O错误
     */
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入用户名: ");
        String username = scanner.nextLine();

        ChatClient client = new ChatClient(username);
        client.connect("127.0.0.1", 12345);

        System.out.println("已连接到聊天室，可以开始发送消息了。输入 /private <用户名> <消息> 发送私聊，或直接输入消息发送群聊。");
        // 在主线程中循环读取用户输入并发送消息
        while (true) {
            String input = scanner.nextLine();
            if (input.startsWith("/private ")) {
                // 处理私聊命令: /private <username> <message>
                String[] parts = input.split(" ", 3); // 分割为命令、用户名、消息内容
                if (parts.length >= 3) {
                    String targetUsername = parts[1];
                    String privateContent = parts[2];
                    client.sendMessage(new Message(username, targetUsername, privateContent, true, "PRIVATE_CHAT"));
                } else {
                    System.out.println("[系统] 私聊命令格式错误。请使用: /private <用户名> <消息>");
                }
            } else if (input.startsWith("/list")) {
                // 客户端请求在线用户列表（服务器会自动发送，此处仅为提示，无需额外发送消息）
                System.out.println("[系统] 正在请求在线用户列表...");
                // 服务器会在用户加入/离开时自动发送列表，这里无需发送额外请求
                // 如果需要客户端主动请求，可以发送一个特定COMMAND的消息给服务器
            } else {
                // 默认发送群聊消息
                client.sendMessage(new Message(username, null, input, false, "CHAT"));
            }
        }
    }
}