# ChatVibe Backend

> 融合真人聊天与 AI 对话的即时通讯平台 —— 后端服务

ChatVibe 是一款轻量级、现代化的聊天平台，支持私聊、群聊实时通信，并内置 AI 智能助手（@AI 召唤），让 AI 嵌入自然沟通流之中。本仓库为后端工程，基于 **Spring Boot 3 + MyBatis-Plus + WebSocket** 构建，集成阿里云百炼大模型提供 AI 对话服务。

**在线演示**：[cv.chatvibe.icu](https://cv.chatvibe.icu)
**前端仓库**：[chatvibe-frontend](https://github.com/ALu9740/chatvibe-frontend)（Vue 3 + Element Plus + Pinia）

---

## 核心特性

- **实时通信**：基于 STOMP 协议的 WebSocket 长连接，支持私聊、群聊、在线状态、强制下线 4 种消息路由策略
- **AI 流式对话**：对接阿里云百炼（Qwen）/ OpenAI / 本地 Ollama，基于 WebClient + SSE 实现逐字流式输出，支持多轮上下文
- **多 AI 提供商切换**：策略模式（`@ConditionalOnProperty`）实现零代码切换，dev 用 Ollama，prod 用百炼
- **限流与降级**：Redis 滑动窗口限流（20 次/分钟）+ 主服务失败自动切换兜底 AI 服务
- **安全认证**：JWT 双 Token（Access/Refresh）+ `login_version` 版本控制实现单点登录（SSO），新设备登录强制旧设备下线
- **多级缓存**：针对在线状态、会话列表、未读计数等高频数据设计差异化 Redis 缓存策略
- **完整业务闭环**：好友管理、群组管理、消息撤回、会话置顶/免打扰、文件传输、通知系统

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.2.5 | 应用框架 |
| Spring Security | 6.2+ | 认证授权 |
| Spring WebSocket | 6.2+ | 实时通信（STOMP 协议） |
| Spring WebFlux | - | WebClient 调用 AI 流式接口 |
| MyBatis-Plus | 3.5.5 | ORM |
| Spring Mail | - | 邮件验证码 |
| Redis (Lettuce) | - | 缓存 / 会话 / 限流 / 验证码 |
| MySQL | 8.0 | 主数据库 |
| JJWT | 0.12.3 | JWT 令牌 |
| Hutool | 5.8.25 | 工具类 |
| Lombok | - | 简化代码 |
| Maven | 3.9+ | 依赖管理 |

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      浏览器 Vue 3 SPA                        │
│            (REST API / axios  +  WebSocket / SockJS)         │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        ▼                             ▼
┌───────────────────┐      ┌────────────────────┐
│  REST Controller  │      │  WebSocket 端点     │
│   /api/** (JWT)   │      │  /ws-chat (JWT握手) │
└────────┬──────────┘      └─────────┬──────────┘
         │                           │
         ▼                           ▼
┌─────────────────────────────────────────────────┐
│                  Service 层（业务逻辑）            │
│  Auth · User · Chat · Friend · Group · AI · File │
└─────┬──────────┬──────────┬──────────┬──────────┘
      │          │          │          │
      ▼          ▼          ▼          ▼
┌──────────┐ ┌────────┐ ┌────────┐ ┌──────────────┐
│ MyBatis- │ │ Redis  │ │  Mail  │ │  AI Service  │
│  Plus    │ │ Cache  │ │  163   │ │ (SSE Stream) │
└────┬─────┘ └────────┘ └────────┘ └──────┬───────┘
     │                                    │
     ▼                                    ▼
┌──────────┐                    ┌──────────────────┐
│ MySQL 8  │                    │ 阿里云百炼 Qwen   │
│          │                    │ / Ollama / OpenAI │
└──────────┘                    └──────────────────┘
```

---

## 项目结构

```
src/main/java/com/chatvibe/
├── ChatVibeApplication.java          # 启动类
├── config/                           # 配置类
│   ├── SecurityConfig.java           #   Spring Security 配置
│   ├── WebSocketConfig.java          #   WebSocket + STOMP 配置
│   ├── RedisConfig.java              #   Redis 序列化配置
│   ├── MybatisPlusConfig.java        #   分页插件 + 逻辑删除
│   ├── CorsConfig.java               #   跨域配置
│   ├── AiConfig.java                 #   AI 服务条件装配
│   └── WebMvcConfig.java             #   静态资源 + 拦截器
├── security/                         # 安全模块
│   ├── JwtUtil.java                  #   JWT 生成/验证
│   ├── JwtAuthenticationFilter.java  #   JWT 过滤器（含 login_version 校验）
│   ├── JwtAuthenticationEntryPoint.java
│   ├── SecurityUtils.java            #   获取当前登录用户
│   ├── LoginUser.java                #   SecurityUser 封装
│   └── UserDetailsServiceImpl.java
├── common/                           # 公共模块
│   ├── result/                       #   Result / ResultCode / PageResult
│   ├── exception/                    #   全局异常处理
│   └── entity/                       #   BaseEntity
├── websocket/                        # WebSocket 处理
│   ├── ChatWebSocketHandler.java     #   消息路由处理器
│   ├── JwtHandshakeInterceptor.java  #   JWT 握手鉴权拦截器
│   ├── WebSocketEventListener.java   #   连接/断开事件监听
│   └── dto/                          #   WsMessage / WsStatusMessage
└── module/                           # 业务模块（每个模块统一分层）
    ├── auth/                         #   认证：注册/登录/验证码/密码重置
    ├── user/                         #   用户：资料/头像/密码/邮箱绑定
    ├── chat/                         #   聊天：会话/消息/已读/置顶/免打扰
    ├── friend/                       #   好友：搜索/请求/接受/拒绝
    ├── group/                        #   群组：创建/编辑/邀请/移除/退出
    ├── ai/                           #   AI：流式对话/多轮上下文/降级兜底
    ├── file/                         #   文件：上传/访问
    └── notification/                 #   通知：好友请求/群邀请/系统通知

# 每个模块内部结构：
module/{name}/
├── controller/    # REST 接口
├── service/       # 接口 + impl 实现
├── mapper/        # MyBatis-Plus Mapper
├── entity/        # 数据库实体
├── dto/           # 请求入参
├── vo/            # 返回视图
└── enums/         # 枚举（部分模块）
```

---

## 快速开始

### 环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 必须 |
| Maven | 3.9+ | 构建工具 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 6.0+ | 缓存/会话 |
| Ollama | 最新 | 本地 AI 推理（开发环境可选） |

### 1. 克隆仓库

```bash
git clone https://github.com/ALu9740/chatvibe-backend.git
cd chatvibe-backend
```

### 2. 数据库初始化

```sql
CREATE DATABASE chatvibe DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行初始化脚本（包含 8 张表 + 测试数据）：

```bash
mysql -u root -p chatvibe < src/main/resources/db/schema.sql
```

### 3. 配置修改

修改 `src/main/resources/application-dev.yml`，填入你的本地环境配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/chatvibe?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 你的MySQL密码
  data:
    redis:
      host: localhost
      port: 6379
      password: 你的Redis密码     # 无密码则留空
  mail:
    username: 你的邮箱@163.com
    password: 你的163邮箱授权码    # 非登录密码，在163邮箱设置中开启SMTP获取
```

### 4. AI 服务配置

在 `application.yml` 中切换 AI 提供商（三选一）：

```yaml
chatvibe:
  ai:
    provider: qwen    # qwen（阿里云百炼）| ollama（本地）| openai
```

**阿里云百炼（推荐，生产环境）**：
```bash
# 获取 API Key: https://bailian.console.aliyun.com/ → 模型广场 → API-KEY 管理
export QWEN_API_KEY=sk-your-dashscope-api-key
```

**本地 Ollama（开发环境）**：
```bash
ollama pull deepseek-r1:8b
ollama serve    # 默认 http://localhost:11434
```

**OpenAI 兼容接口**：
```bash
export AI_API_KEY=sk-your-key
export AI_API_BASE=https://api.openai.com/v1/chat/completions
```

### 5. 启动

```bash
mvn spring-boot:run
```

后端启动在 `http://localhost:8080`。

**测试账号**（schema.sql 已插入）：

| 邮箱 | 密码 | 角色 |
|------|------|------|
| alice@chatvibe.com | password | 用户 |
| bob@chatvibe.com | password | 用户 |
| admin@chatvibe.com | password | 管理员 |

---

## 核心模块说明

### AI 对话模块

AI 模块是本项目的核心亮点，支持流式输出、多轮上下文、限流降级。

**流程**：
1. 用户在聊天中输入 `@AI` + 问题，前端通过 WebSocket 落库用户消息并广播给会话所有成员
2. 前端调用 `POST /api/ai/chat`（SSE），后端从会话历史构建上下文（最近 20 条非系统消息）
3. 通过 WebClient 调用大模型 API（`stream=true`），逐 token 通过 SSE 推送给前端
4. 主服务失败时自动切换 `AiFallbackServiceImpl` 兜底
5. AI 回复完成后落库到常规会话表，并通过 WebSocket 广播给所有成员

**多提供商策略模式**：

```
AiService (接口)
├── QwenAiServiceImpl    @ConditionalOnProperty(provider=qwen)    阿里云百炼
├── OpenAiServiceImpl    @ConditionalOnProperty(provider=openai)  OpenAI 兼容
├── OllamaAiServiceImpl  @ConditionalOnProperty(provider=ollama)  本地 Ollama
└── AiFallbackServiceImpl                                         降级兜底（始终生效）
```

切换提供商只需改一行配置，无需改代码。每个实现都注入系统提示词（`"你是vibe助手，由Alu打造的一款智能助手"`）确保 AI 身份一致。

**限流**：基于 Redis 计数器，单用户每分钟 20 次，超限返回 `AI_LIMIT_EXCEEDED`。

### WebSocket 实时通信

**连接**：`ws://localhost:8080/ws-chat?token={JWT}`（握手时通过 `JwtHandshakeInterceptor` 校验 Token）

**消息路由策略**：

| 策略 | 订阅频道 | 场景 |
|------|---------|------|
| 会话消息 | `/topic/conversation.{convId}` | 私聊/群聊消息广播 |
| 在线状态 | `/topic/status` | 用户上下线状态变更 |
| 强制下线 | `/topic/user.{userId}.force-logout` | 新设备登录通知旧设备下线 |

**发送消息**：
```javascript
stompClient.publish({
  destination: '/app/chat.send',
  body: JSON.stringify({ conversationId: 1, type: 'TEXT', content: '你好' })
})
```

### 安全认证

| 维度 | 实现 |
|------|------|
| 身份认证 | JWT (HS256) + Spring Security，Access Token 2h / Refresh Token 7d |
| 单点登录 | `login_version` 版本号控制，新设备登录使旧 Token 失效（错误码 2011） |
| 密码存储 | BCrypt 加密 |
| 接口权限 | `@PreAuthorize` + SecurityFilterChain 白名单 |
| WebSocket | 握手拦截器校验 JWT |
| SQL 注入 | MyBatis-Plus 参数化查询 |
| 验证码 | Redis 存储 + 5min 过期 + 60s 发送限流 |
| 登出 | JWT 黑名单（Redis，TTL = Token 剩余有效期） |

### 多级缓存策略

| Redis Key | TTL | 用途 |
|-----------|-----|------|
| `user:online:{uid}` | 会话级 | 在线状态（断开不清除偏好） |
| `user:info:{uid}` | 30min | 用户信息缓存 |
| `conv:list:{uid}` | 10min | 会话列表缓存 |
| `msg:unread:{uid}:{convId}` | 持久 | 未读消息计数 |
| `code:verify:{email}` | 5min | 邮箱验证码 |
| `code:verify:limit:{email}` | 60s | 验证码发送限流 |
| `jwt:blacklist:{token}` | Token 剩余有效期 | 登出令牌黑名单 |
| `ai:limit:{uid}` | 1min | AI 调用限流计数 |

---

## API 接口概览

统一响应格式：
```json
{ "code": 200, "message": "success", "data": {} }
```

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 认证 | POST | `/api/auth/register` | 注册（邮箱+验证码+密码） |
| 认证 | POST | `/api/auth/send-code` | 发送邮箱验证码 |
| 认证 | POST | `/api/auth/login` | 登录，返回 JWT 双 Token |
| 认证 | POST | `/api/auth/refresh` | 刷新 Access Token |
| 认证 | POST | `/api/auth/logout` | 登出（Token 加入黑名单） |
| 用户 | GET | `/api/user/profile` | 获取当前用户信息 |
| 用户 | PUT | `/api/user/profile` | 更新资料 |
| 用户 | PUT | `/api/user/password` | 修改密码 |
| 用户 | POST | `/api/user/avatar` | 上传头像 |
| 好友 | GET | `/api/friend/list` | 好友列表 |
| 好友 | POST | `/api/friend/search` | 搜索用户 |
| 好友 | POST | `/api/friend/request` | 发送好友请求 |
| 好友 | GET | `/api/friend/requests` | 好友请求列表 |
| 好友 | PUT | `/api/friend/request/{id}` | 处理请求（accept/reject） |
| 群组 | POST | `/api/group/create` | 创建群组 |
| 群组 | GET | `/api/group/list` | 群组列表 |
| 群组 | PUT | `/api/group/{id}` | 编辑群信息 |
| 群组 | POST | `/api/group/{id}/members` | 邀请成员 |
| 群组 | DELETE | `/api/group/{id}/members/{uid}` | 移除成员 |
| 群组 | POST | `/api/group/{id}/leave` | 退出群组 |
| 聊天 | GET | `/api/chat/conversations` | 会话列表 |
| 聊天 | GET | `/api/chat/messages/{convId}` | 历史消息（分页） |
| 聊天 | POST | `/api/chat/message` | 发送消息 |
| AI | POST | `/api/ai/chat` | AI 流式对话（SSE） |
| AI | GET | `/api/ai/conversations` | AI 会话列表 |
| 文件 | POST | `/api/file/upload` | 文件上传 |
| 通知 | GET | `/api/notification/list` | 通知列表 |

完整接口文档可导入 Apifox / Postman 查看。

---

## 数据库设计

共 8 张核心表：

| 表名 | 说明 |
|------|------|
| `user` | 用户表（邮箱、密码、昵称、头像、状态、login_version） |
| `conversation` | 会话表（type: 1私聊 2群聊 3AI） |
| `conversation_member` | 会话成员表（角色、未读数、置顶、免打扰、软删除） |
| `message` | 消息表（type: 1文本 2图片 3文件 4系统，senderId=0 为 AI） |
| `message_hidden` | 用户删除消息记录表（用户视角隐藏，物理保留） |
| `friend_request` | 好友请求表（from/to/status） |
| `group_member` | 群成员扩展表（role: OWNER/ADMIN/MEMBER） |
| `verification_code` | 验证码表（email/code/type/expired） |

建表脚本见 `src/main/resources/db/schema.sql`。

---

## 部署

### 打包

```bash
mvn clean package -DskipTests
# 产物: target/chatvibe-backend-1.0.0.jar
```

### 生产环境运行

```bash
# 通过环境变量注入敏感配置
export SPRING_PROFILES_ACTIVE=prod
export MYSQL_HOST=your-mysql-host
export MYSQL_PASSWORD=your-mysql-password
export REDIS_HOST=your-redis-host
export REDIS_PASSWORD=your-redis-password
export QWEN_API_KEY=your-dashscope-api-key
export MAIL_PASSWORD=your-mail-auth-code

java -jar target/chatvibe-backend-1.0.0.jar
```

### Nginx 反向代理参考

```nginx
server {
    listen 443 ssl;
    server_name cv.chatvibe.icu;

    # SSL 证书
    ssl_certificate     /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    # API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket 反向代理
    location /ws-chat {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }

    # 静态文件
    location /uploads/ {
        alias /opt/chatvibe/uploads/;
    }
}
```

### systemd 服务（推荐）

```bash
# /etc/systemd/system/chatvibe-backend.service
[Unit]
Description=ChatVibe Backend
After=network.target mysql.service redis.service

[Service]
Type=simple
User=www
WorkingDirectory=/opt/chatvibe
ExecStart=/usr/bin/java -jar /opt/chatvibe/chatvibe-backend-1.0.0.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable chatvibe-backend
sudo systemctl start chatvibe-backend
```

---

## 测试

项目集成了 JUnit 5 + Spring Boot Test + H2 内存数据库，并使用 JaCoCo 统计代码覆盖率（目标 90%）。

```bash
# 运行测试
mvn test

# 查看覆盖率报告
open target/site/jacoco/index.html
```

覆盖率报告输出在 `target/site/jacoco/`（HTML / XML / CSV 三种格式）。

---

## 开发命令速查

```bash
mvn spring-boot:run                                    # 启动开发服务器
mvn compile                                            # 编译检查
mvn test                                               # 运行测试 + 生成覆盖率
mvn package -DskipTests                                # 打包（跳过测试）
mvn spring-boot:run -Dspring-boot.run.profiles=prod    # 生产 profile 启动
```

---

## License

MIT License

---

> 作者：Alu ([@ALu9740](https://github.com/ALu9740)) 
>
> 如果这个项目对你有帮助，欢迎 Star ⭐
