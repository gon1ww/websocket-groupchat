package com.example.groupchatdemo.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

/**
 * 自定义握手拦截器，用于在WebSocket握手时设置用户的Principal。
 * 这确保了Spring WebSocket可以根据用户名正确路由私聊消息。
 */
public class UserInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpSession session = servletRequest.getServletRequest().getSession();

            // 从HTTP请求参数中获取用户名（app.js中通过查询参数传递）
            String username = servletRequest.getServletRequest().getParameter("username");

            if (username != null && !username.isEmpty()) {
                // 设置Principal，Spring WebSocket将使用它来识别用户
                // 这里使用了一个简单的Lambda表达式作为Principal的实现
                attributes.put("principal", (Principal) () -> username);
                System.out.println("WebSocket Handshake: Principal set for user: " + username);
                return true;
            } else {
                System.out.println("WebSocket Handshake: Username not found in request parameters.");
                return false; // 如果没有用户名，拒绝握手
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 握手后处理，目前不需要特殊逻辑
    }
} 