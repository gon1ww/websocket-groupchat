let stompClient = null;
let username = null;
let activeChatTarget = 'public'; // 当前活跃的聊天对象：'public' 或某个私聊用户名
const privateChatWindows = new Map(); // 存储私聊窗口的Map，键为用户名

// 获取DOM元素
const usernamePage = document.querySelector('#usernamePage');
const chatPage = document.querySelector('#chatPage');
const usernameInput = document.querySelector('#usernameInput');
const connectButton = document.querySelector('#connectButton');

const publicChatListItem = document.querySelector('#publicChatListItem');
const publicChatNotification = document.querySelector('#publicChatNotification');

const onlineUsersList = document.querySelector('#onlineUsersContainer');
const chatWindowsContainer = document.querySelector('#chatWindowsContainer');
const publicChatWindow = document.querySelector('#publicChatWindow');
const publicMessageArea = document.querySelector('#publicMessageArea');
const publicMessageInput = document.querySelector('#publicMessageInput');
const publicSendButton = document.querySelector('#publicSendButton');
const privateChatWindowTemplate = document.querySelector('#privateChatWindowTemplate');

// 连接WebSocket
function connect() {
    username = usernameInput.value.trim();

    if (username) {
        usernamePage.classList.add('d-none');
        chatPage.classList.remove('d-none');

        // 不再将用户名作为查询参数添加到WebSocket URL
        const socket = new SockJS('/ws'); // 连接到WebSocketConfig中配置的端点
        stompClient = Stomp.over(socket);

        // 在连接时将用户名作为自定义STOMP头发送
        const headers = {
            'username': username
        };
        stompClient.connect(headers, onConnected, onError); // 连接成功或失败的回调
    } else {
        alert('请输入用户名！');
    }
}

// WebSocket连接成功回调
function onConnected() {
    console.log("Client: onConnected triggered.");
    // 订阅公共聊天主题
    stompClient.subscribe('/topic/public', onMessageReceived);
    // 订阅私聊消息队列（每个用户有独立的队列）
    stompClient.subscribe('/user/queue/messages', onMessageReceived, {'id': 'private-messages-sub'});
    console.log("Client: Subscribed to /user/queue/messages");

    // 发送用户加入消息给服务器
    stompClient.send("/app/chat.addUser",
        {},
        JSON.stringify({from: username, command: 'LOGIN'})
    );
    console.log('Client: Connected to WebSocket. Sending user login message.');

    // 初始显示公共聊天窗口
    showChatWindow('public');
}

// WebSocket连接失败回调
function onError(error) {
    // 使用公共消息区域显示错误信息
    publicMessageArea.innerHTML = '<div class="system-message">无法连接到WebSocket服务器。请刷新页面重试。</div>';
    console.error('WebSocket connection error:', error);
}

// 发送消息 (公共或私聊)
function sendMessage() {
    let messageContent;
    let messageType;
    let targetUser = null;

    if (activeChatTarget === 'public') {
        messageContent = publicMessageInput.value.trim();
        messageType = 'CHAT';
    } else {
        // 私聊消息
        const privateMessageInput = privateChatWindows.get(activeChatTarget).querySelector('.private-message-input');
        messageContent = privateMessageInput.value.trim();
        messageType = 'PRIVATE_CHAT';
        targetUser = activeChatTarget;
    }

    if (messageContent && stompClient) {
        const chatMessage = {
            from: username,
            to: targetUser, // 如果是私聊，则有to属性
            content: messageContent,
            isPrivate: (messageType === 'PRIVATE_CHAT'),
            command: messageType
        };

        if (messageType === 'CHAT') {
            stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
            publicMessageInput.value = ''; // 清空公共输入框
        } else if (messageType === 'PRIVATE_CHAT') {
            stompClient.send("/app/chat.sendPrivateMessage", {}, JSON.stringify(chatMessage));
            privateChatWindows.get(activeChatTarget).querySelector('.private-message-input').value = ''; // 清空私聊输入框
        }
    }
}

// 接收到消息时的处理
function onMessageReceived(payload) {
    console.log(`Client: activeChatTarget at message receipt: ${activeChatTarget}`);
    const message = JSON.parse(payload.body);
    console.log('Client: Received full message payload:', payload);
    console.log('Client: Received message object:', message);
    console.log('Client: Message command:', message.command);
    console.log('Client: Message isPrivate:', message.isPrivate);

    // Variables for message display, initialized only when needed
    let messageElement = null;
    let targetMessageArea = null;
    let scrollArea = null;
    let targetNotificationBadge = null;

    switch (message.command) {
        case 'JOIN':
            // 用户加入消息，无须在聊天区域显示，已由USER_LIST_UPDATE处理或仅作日志记录
            return; // 直接返回，不处理后续的消息显示逻辑
        case 'CHAT':
            messageElement = document.createElement('div');
            messageElement.classList.add('message-box');
            targetMessageArea = publicMessageArea;
            scrollArea = publicMessageArea;

            // Add avatar and message content container
            const publicAvatarImg = document.createElement('img');
            publicAvatarImg.classList.add('rounded-circle', 'chat-avatar');

            const publicContentDiv = document.createElement('div');
            publicContentDiv.classList.add('message-content');

            if (message.from === username) {
                messageElement.classList.add('my-message');
                publicAvatarImg.src = `https://ui-avatars.com/api/?name=${username}&background=random&color=fff&size=30`;
                publicAvatarImg.alt = username.charAt(0);
                publicContentDiv.innerHTML = `<strong>你:</strong> ${message.content}`;
            } else {
                messageElement.classList.add('other-message');
                publicAvatarImg.src = `https://ui-avatars.com/api/?name=${message.from}&background=random&color=fff&size=30`;
                publicAvatarImg.alt = message.from.charAt(0);
                publicContentDiv.innerHTML = `<strong>${message.from}:</strong> ${message.content}`;
            }
            messageElement.appendChild(publicAvatarImg);
            messageElement.appendChild(publicContentDiv);
            break;
        case 'PRIVATE_CHAT':
            let chatPartner = (message.from === username) ? message.to : message.from;
            let privateChatWindow = privateChatWindows.get(chatPartner);

            if (!privateChatWindow) {
                privateChatWindow = createPrivateChatWindow(chatPartner);
                console.log("PRIVATE_CHAT: Created new privateChatWindow for", chatPartner, ":", privateChatWindow);
            } else {
                console.log("PRIVATE_CHAT: Found existing privateChatWindow for", chatPartner, ":", privateChatWindow);
            }

            if (message.command === 'PRIVATE_CHAT') {
                messageElement = document.createElement('div');
                messageElement.classList.add('message-box');
                targetMessageArea = privateChatWindow.querySelector('.private-message-area');
                scrollArea = targetMessageArea;
                targetNotificationBadge = onlineUsersList.querySelector(`[data-username="${chatPartner}"] .notification-badge`);

                const privateAvatarImg = document.createElement('img');
                privateAvatarImg.classList.add('rounded-circle', 'chat-avatar');

                const privateContentDiv = document.createElement('div');
                privateContentDiv.classList.add('message-content');

                console.log("PRIVATE_CHAT: targetMessageArea:", targetMessageArea);
                console.log("PRIVATE_CHAT: scrollArea:", scrollArea);
                console.log("PRIVATE_CHAT: messageElement:", messageElement);
                console.log("PRIVATE_CHAT: Processing private message for", chatPartner);

                if (message.from === username) {
                    messageElement.classList.add('my-message');
                    privateAvatarImg.src = `https://ui-avatars.com/api/?name=${username}&background=random&color=fff&size=30`;
                    privateAvatarImg.alt = username.charAt(0);
                    privateContentDiv.innerHTML = `<strong>你 对 ${message.to}:</strong> ${message.content} (私聊)`;
                } else {
                    messageElement.classList.add('other-message');
                    privateAvatarImg.src = `https://ui-avatars.com/api/?name=${message.from}&background=random&color=fff&size=30`;
                    privateAvatarImg.alt = message.from.charAt(0);
                    privateContentDiv.innerHTML = `<strong>[私聊] ${message.from}:</strong> ${message.content}`;
                }
                messageElement.appendChild(privateAvatarImg);
                messageElement.appendChild(privateContentDiv);

                if (activeChatTarget !== chatPartner && chatPartner !== username) {
                    updateNotificationBadge(targetNotificationBadge, 1);
                }
            } else {
                console.warn("PRIVATE_CHAT: Received message with command PRIVATE_CHAT but invalid structure:", message);
            }
            break;
        case 'USER_LIST_UPDATE':
            console.log('Client: Processing USER_LIST_UPDATE. Content:', message.content);
            updateOnlineUsers(message.content);
            return; // 直接返回，不处理后续的消息显示和滚动逻辑
        case 'SERVER_INFO':
            messageElement = document.createElement('div');
            messageElement.classList.add('message-box');
            targetMessageArea = publicMessageArea;
            scrollArea = publicMessageArea;
            messageElement.classList.add('system-message');
            messageElement.innerHTML = `<strong>[系统消息]:</strong> ${message.content}`;
            break;
        default:
            messageElement = document.createElement('div');
            messageElement.classList.add('message-box');
            targetMessageArea = publicMessageArea;
            scrollArea = publicMessageArea;
            messageElement.classList.add('system-message');
            messageElement.innerHTML = `<strong>[未知消息]:</strong> ${message.content}`;
            break;
    }

    // 只有当messageElement、targetMessageArea和scrollArea都被有效设置时才进行DOM操作和滚动
    if (messageElement && targetMessageArea && scrollArea) {
        targetMessageArea.appendChild(messageElement);
        scrollArea.scrollTop = scrollArea.scrollHeight;
    }
}

// 更新在线用户列表，并添加点击事件和头像
function updateOnlineUsers(userListString) {
    console.log('Client: updateOnlineUsers called with string:', userListString);
    // 清空现有列表，保留公共聊天室条目
    const existingUsers = onlineUsersList.querySelectorAll('li:not(#publicChatListItem)');
    console.log('Client: updateOnlineUsers: Found existing users (excluding public):', existingUsers.length);
    existingUsers.forEach(li => li.remove());

    const users = userListString.split(',').filter(u => {
        const trimmedUser = u.trim();
        const shouldInclude = trimmedUser !== '' && trimmedUser !== username;
        console.log(`Client: updateOnlineUsers: Filtering user: "${trimmedUser}" (current username: "${username}"), Include: ${shouldInclude}`);
        return shouldInclude;
    });

    console.log('Client: updateOnlineUsers: Filtered users to add:', users);

    if (users.length === 0 && userListString.trim() !== '') {
        console.warn('Client: updateOnlineUsers: No users were added to the list despite receiving content. Check filtering logic.');
    }

    users.forEach(user => {
        const li = document.createElement('li');
        li.classList.add('list-group-item');
        li.setAttribute('data-username', user.trim());

        const avatarImg = document.createElement('img');
        avatarImg.src = `https://ui-avatars.com/api/?name=${user.trim()}&background=random&color=fff&size=30`;
        avatarImg.alt = user.trim().charAt(0);
        avatarImg.classList.add('rounded-circle');

        const usernameSpan = document.createElement('span');
        usernameSpan.textContent = user.trim();

        const notificationBadge = document.createElement('span');
        notificationBadge.classList.add('notification-badge', 'd-none');
        notificationBadge.textContent = '0';

        li.appendChild(avatarImg);
        li.appendChild(usernameSpan);
        li.appendChild(notificationBadge);

        // 添加点击事件，切换到私聊窗口
        li.addEventListener('click', () => showChatWindow(user.trim()));

        onlineUsersList.appendChild(li);
        console.log(`Client: updateOnlineUsers: Appended user "${user.trim()}" to list.`);
    });
    console.log('Client: Online users updated (final check).');
}

// 显示指定聊天窗口
function showChatWindow(target) {
    console.log(`Client: showChatWindow called with target: ${target}`);
    // 隐藏所有聊天窗口
    document.querySelectorAll('.chat-window').forEach(window => {
        if (!window.classList.contains('d-none')) {
            console.log(`Client: Hiding window: ${window.id}`);
        }
        window.classList.add('d-none');
    });
    // 移除所有用户列表项的active状态
    document.querySelectorAll('#onlineUsersContainer .list-group-item').forEach(item => {
        item.classList.remove('active');
    });

    let targetWindow;
    let targetListItem;
    if (target === 'public') {
        targetWindow = publicChatWindow;
        targetListItem = publicChatListItem;
        // 清除公共聊天室的通知
        publicChatNotification.classList.add('d-none');
        publicChatNotification.textContent = '0';
    } else {
        // 私聊窗口
        targetWindow = privateChatWindows.get(target);
        if (!targetWindow) {
            // 如果私聊窗口不存在，先创建它
            targetWindow = createPrivateChatWindow(target);
        }
        targetListItem = onlineUsersList.querySelector(`[data-username="${target}"]`);
        // 清除私聊的通知
        const notificationBadge = targetListItem.querySelector('.notification-badge');
        if (notificationBadge) {
            notificationBadge.classList.add('d-none');
            notificationBadge.textContent = '0';
        }
    }

    if (targetWindow) {
        targetWindow.classList.remove('d-none');
        // 滚动到底部
        const scrollArea = targetWindow.querySelector('.messages');
        if (scrollArea) {
            scrollArea.scrollTop = scrollArea.scrollHeight;
        }
    }
    if (targetListItem) {
        targetListItem.classList.add('active');
    }
    activeChatTarget = target;
}

// 动态创建私聊聊天窗口
function createPrivateChatWindow(chatPartner) {
    console.log("createPrivateChatWindow: Attempting to create window for:", chatPartner);
    const templateContent = privateChatWindowTemplate.content.cloneNode(true);
    const privateChatWindow = templateContent.querySelector('.chat-window');
    privateChatWindow.setAttribute('data-username', chatPartner);
    privateChatWindow.id = `privateChatWindow-${chatPartner}`;

    privateChatWindow.querySelector('.private-chat-username').textContent = chatPartner;

    const closeButton = document.createElement('button');
    closeButton.classList.add('btn', 'btn-sm', 'btn-danger', 'close-private-chat');
    closeButton.textContent = 'X';
    closeButton.onclick = (event) => {
        event.stopPropagation(); // Prevent triggering the showChatWindow when closing
        privateChatWindow.classList.add('d-none');
        privateChatWindow.remove();
        privateChatWindows.delete(chatPartner);
        // If the closed window was the active one, switch to public chat
        if (activeChatTarget === chatPartner) {
            showChatWindow('public');
        }
    };
    privateChatWindow.querySelector('.chat-window-header').appendChild(closeButton);

    // 设置私聊窗口的头像
    const privateChatAvatar = privateChatWindow.querySelector('.chat-window-header img');
    if (privateChatAvatar) {
        privateChatAvatar.src = `https://ui-avatars.com/api/?name=${chatPartner}&background=random&color=fff&size=30`;
        privateChatAvatar.alt = chatPartner.charAt(0);
        console.log("createPrivateChatWindow: Avatar src set to:", privateChatAvatar.src);
    } else {
        console.error("createPrivateChatWindow: Could not find avatar image element for:", chatPartner);
    }

    const privateMessageArea = privateChatWindow.querySelector('.private-message-area');
    privateMessageArea.id = `privateMessageArea-${chatPartner}`;

    const privateMessageInput = privateChatWindow.querySelector('.private-message-input');
    privateMessageInput.id = `privateMessageInput-${chatPartner}`;

    const privateSendButton = privateChatWindow.querySelector('.private-send-button');
    privateSendButton.id = `privateSendButton-${chatPartner}`;

    // 添加私聊发送按钮的事件监听器
    privateSendButton.addEventListener('click', () => {
        activeChatTarget = chatPartner; // 确保发送消息时目标正确
        sendMessage();
    });

    // 添加私聊输入框的回车事件监听器
    privateMessageInput.addEventListener('keyup', (event) => {
        if (event.key === 'Enter') {
            activeChatTarget = chatPartner; // 确保发送消息时目标正确
            sendMessage();
        }
    });

    chatWindowsContainer.appendChild(privateChatWindow);
    privateChatWindows.set(chatPartner, privateChatWindow);
    console.log("Created private chat window for:", chatPartner);
    return privateChatWindow;
}

// 更新通知徽章
function updateNotificationBadge(badgeElement, countChange) {
    if (badgeElement) {
        let currentCount = parseInt(badgeElement.textContent);
        let newCount = currentCount + countChange;
        if (newCount > 0) {
            badgeElement.textContent = newCount;
            badgeElement.classList.remove('d-none');
        } else {
            badgeElement.textContent = '0';
            badgeElement.classList.add('d-none');
        }
    }
}

// 绑定事件监听器
connectButton.addEventListener('click', connect);
usernameInput.addEventListener('keyup', (event) => {
    if (event.key === 'Enter') {
        connect();
    }
});
publicSendButton.addEventListener('click', sendMessage);
publicMessageInput.addEventListener('keyup', (event) => {
    if (event.key === 'Enter') {
        sendMessage();
    }
});

publicChatListItem.addEventListener('click', () => showChatWindow('public'));