# ChatVibe

> 融合真人聊天与 AI 对话的全栈即时通讯平台

<p>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.3.5">
  <img src="https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vuedotjs&logoColor=white" alt="Vue 3.5">
  <img src="https://img.shields.io/badge/TypeScript-5.6-3178C6?logo=typescript&logoColor=white" alt="TypeScript 5.6">
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white" alt="MySQL 8.0">
  <img src="https://img.shields.io/badge/Redis-8.0-DC382D?logo=redis&logoColor=white" alt="Redis 8.0">
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="MIT License">
</p>

ChatVibe 是一个前后端分离的即时通讯系统，涵盖私聊、群聊、好友体系、通知中心等核心 IM 能力，并内置 AI 智能对话模块——在群聊中 @AI 即可触发大模型推理，通过 SSE 流式回复将答案实时推送给所有在线成员。系统同时配备完整的管理员后台，支持数据概览、用户管理、消息审计、AI 服务管理、系统公告、系统配置与操作日志等运维功能。后端基于 **Spring Boot 3.3.5** 构建，前端采用 **Vue 3.5 + TypeScript** 全家桶，所有中间件（Redis、RabbitMQ、MinIO、Ollama）部署于单台 Ubuntu 虚拟机，适合作为全栈项目学习与二次开发的基础。

---

## 目录

- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [环境配置](#环境配置)
- [数据库设计](#数据库设计)
- [AI 模块](#ai-模块)
- [管理后台](#管理后台)
- [工程实践](#工程实践)
- [测试](#测试)
- [License](#license)

---

## 核心功能

### 即时通讯

- **实时消息收发**：基于 WebSocket (STOMP + SockJS) 实现全双工通信，文字、图片、文件消息秒级送达
- **消息类型**：支持文本、图片、语音、文件、系统消息五种类型
- **会话管理**：私聊与群聊统一会话模型，支持置顶、免打扰、已读回执、消息隐藏、清空聊天记录
- **历史消息**：基于游标的分页查询，消息按时间线渲染，支持用户级消息隐藏（仅对操作者不可见）

### 群组协作

- **群组全生命周期**：创建群组、邀请成员、移除成员、转让群主、解散群组
- **角色权限**：群主、管理员、成员三级角色体系
- **重新加入**：退出或被移除后可通过重新加入功能恢复群聊会话

### 好友体系

- **邮箱搜索**：通过邮箱精确搜索用户，搜索结果使用 Caffeine 本地缓存（1 分钟 TTL）
- **好友请求**：发送验证消息、接受、拒绝、删除好友，全流程 Redis 分布式锁防并发

### AI 智能对话

- **群内 @AI 召唤**：在群聊中发送 @AI 前缀消息，自动路由至大模型推理
- **SSE 流式回复**：AI 回复通过 Server-Sent Events 实时推送，逐字呈现
- **多供应商故障转移**：支持阿里云百炼 (Qwen) 与本地 Ollama 双供应商，按环境配置优先级自动切换
- **上下文记忆**：基于 Spring AI ChatMemory 管理对话上下文，保持多轮对话连贯性

### 通知中心

- **事件驱动通知**：好友请求/接受/删除、群邀请/移除/转让/解散等事件通过 RabbitMQ 异步生成通知
- **未读管理**：实时未读数统计（Caffeine 缓存，30 秒 TTL），支持单条已读、全部已读、删除

### 用户与安全

- **邮箱注册**：163 邮箱 SMTP 验证码注册，密码 BCrypt 加密存储
- **JWT 认证**：Access Token (2 小时) + Refresh Token (7 天) 双令牌机制
- **多设备登录冲突**：通过 login_version 版本号实现后登录设备踢出先登录设备
- **接口级限流**：Resilience4j RateLimiter 覆盖所有 API，防止恶意请求

### 管理后台

- **数据概览仪表盘**：8 项核心指标卡片（总用户、在线、新增、消息、AI 调用、群组、API 可用率、平均响应）+ ECharts 趋势图（用户增长、消息量/AI 调用量、AI 用量分析与供应商占比）+ 中间件健康状态实时监控（MySQL/Redis/RabbitMQ/MinIO）
- **用户管理**：多条件检索（关键词/状态/角色）、封禁/解封、角色变更（USER/OPERATOR/ADMIN/SUPER_ADMIN）、密码重置
- **消息审计**：消息全文检索、按发送者/会话/类型/时间筛选、消息详情查看、违规消息删除
- **群组管理**：群组搜索、群主信息查看、成员数统计、违规群组解散
- **AI 服务管理**：供应商 CRUD 与连接测试、故障转移优先级拖拽配置、AI 对话记录监控与详情查看
- **通知公告**：系统公告创建与发布（全员/指定用户，RabbitMQ 异步推送）、公告历史与撤回（自动删除 C 端通知）、通知发送记录多条件查询
- **系统配置**：限流器参数热更新（Resilience4j RateLimiter）、熔断器参数热更新（CircuitBreaker）、Caffeine 缓存监控与清除、邮件 SMTP 动态配置与测试发送
- **操作日志**：全量管理操作审计（20 种操作类型），支持按操作者/类型/时间检索

---

## 技术栈

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5 | 渐进式前端框架，Composition API + SFC |
| TypeScript | 5.6 | 类型安全，接口与实体严格定义 |
| Vite | 5.4 | 极速冷启动与 HMR 热更新 |
| Pinia | 2.3 | 轻量级状态管理 |
| Vue Router | 4.5 | 前端路由 |
| Element Plus | 2.9 | 企业级 UI 组件库，按需自动导入 |
| SockJS + STOMP.js | - | WebSocket 实时通信客户端 |
| Axios | 1.7 | HTTP 请求 |
| ECharts | 5.5 | 数据可视化图表（管理后台仪表盘） |
| Emoji Mart | - | 表情选择器 |
| Pinyin Pro | - | 中文拼音排序与搜索 |
| Sass | - | CSS 预处理器 |

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.3.5 | 后端主框架，Java 17 |
| Spring Security | - | JWT 认证授权，接口级权限控制 |
| Spring WebSocket | - | STOMP 协议实时消息推送 |
| Spring Mail | - | 邮箱验证码发送 |
| Spring Data Redis | - | 分布式锁、缓存、在线状态 |
| Caffeine | - | 本地缓存（搜索结果、好友列表、群详情等） |
| RabbitMQ | - | 事件驱动异步通知 |
| Resilience4j | 2.2.0 | 限流 + 熔断降级 |
| Spring AI | 1.0.0 | 大模型调用统一抽象 (ChatClient + ChatMemory) |
| MinIO | 8.5.10 | 对象存储（头像、文件） |
| MyBatis-Plus | 3.5.5 | 增强 ORM，CRUD 自动生成 |
| MySQL | 8.0+ | 主数据库 |
| WebFlux | - | WebClient 调用 AI 接口 |
| JJWT | 0.12.3 | JWT 令牌生成与解析 |
| Hutool | 5.8.25 | Java 工具集 |
| JaCoCo | 0.8.11 | 代码覆盖率统计（auth/user 模块 ≥ 90%） |

---

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    前端展示层 (Vue 3 SPA)                  │
│   Vue Router · Pinia · Element Plus · SockJS/STOMP       │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP / WebSocket / SSE
┌────────────────────────┴────────────────────────────────┐
│                  网关安全层 (Spring Security)              │
│          JWT 认证过滤器 · 接口权限控制 · CORS              │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│                  业务服务层 (Controller/Service/Mapper)     │
│                                                          │
│  Auth · User · Friend · Group · Chat · AI · Notification · Admin │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │ Caffeine │  │  Redis   │  │ RabbitMQ │  │   AI    │ │
│  │ 本地缓存  │  │ 分布式锁  │  │ 事件驱动  │  │ Failover│ │
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │
│  ┌──────────────────────────────────────────────────┐   │
│  │           Resilience4j 限流 + 熔断                │   │
│  └──────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│                   数据基础设施层                           │
│    MySQL 8.0 · Redis 8.0 · MinIO · Ollama / Qwen        │
└─────────────────────────────────────────────────────────┘
```

### 关键架构决策

- **事件驱动解耦**：好友请求、群组操作等业务事件通过 RabbitMQ 异步通知，数据库事务提交后（`TransactionSynchronizationManager.afterCommit()`）才发送 MQ 消息，避免回滚导致幽灵通知
- **多级缓存策略**：Caffeine 本地缓存用于读多写少场景（搜索结果 1 分钟、好友列表 5 分钟、群详情 3 分钟），Redis 用于分布式锁和跨实例共享状态；会话列表和历史消息因更新频繁不做缓存
- **分布式锁防并发**：好友请求/接受/拒绝/删除、群组创建/编辑/邀请/移除/退出/转让/解散、标记已读等操作均通过 Redis 分布式锁（3-5 秒 TTL）防止并发冲突
- **限流熔断全覆盖**：Resilience4j 对所有 API 配置 RateLimiter 和 CircuitBreaker，BusinessException 被排除在熔断统计之外以避免误判

---

## 项目结构

```
chatvibe/
├── backend/                          # 后端 (Spring Boot)
│   ├── src/main/java/com/chatvibe/
│   │   ├── ChatVibeApplication.java  # 启动类
│   │   ├── common/                   # 公共模块 (异常处理、统一响应)
│   │   ├── config/                   # 配置类 (AI、缓存、CORS、MinIO、MQ、Redis、Security、WebSocket)
│   │   ├── security/                 # 安全模块 (JWT 过滤器、认证入口点、用户详情)
│   │   ├── websocket/                # WebSocket 处理器 (聊天消息、握手拦截、事件监听)
│   │   └── module/                   # 业务模块
│   │       ├── auth/                 # 认证 (注册、登录、密码重置)
│   │       ├── user/                 # 用户 (资料、头像、通知偏好)
│   │       ├── friend/               # 好友 (请求、列表、删除)
│   │       ├── group/                # 群组 (创建、邀请、移除、转让、解散)
│   │       ├── chat/                 # 聊天 (会话、消息、WebSocket 推送)
│   │       ├── ai/                   # AI (对话管理、SSE 流式、故障转移)
│   │       ├── notification/         # 通知 (列表、未读数、已读、删除)
│   │       ├── admin/                # 管理后台 (仪表盘、用户/消息/群组管理、AI服务、公告、配置、日志)
│   │       └── file/                 # 文件 (MinIO 存储)
│   ├── src/main/resources/
│   │   ├── application.yml           # 主配置
│   │   ├── application-dev.yml       # 开发环境配置
│   │   ├── application-prod.yml      # 生产环境配置
│   │   ├── db/schema.sql             # 数据库初始化脚本
│   │   └── mapper/                   # MyBatis XML 映射
│   ├── .env.example                  # 环境变量模板
│   └── pom.xml
│
├── frontend/                         # 前端 (Vue 3)
│   ├── src/
│   │   ├── api/                      # API 请求层 (auth、chat、friend、group、ai、notification、file、user)
│   │   ├── components/               # 组件 (聊天会话列表、消息气泡、表情选择器、主题切换)
│   │   ├── composables/              # 组合式函数 (WebSocket 连接、AI SSE 流)
│   │   ├── layouts/                  # 布局 (聊天主布局、管理员后台布局)
│   │   ├── router/                   # 路由配置
│   │   ├── stores/                   # Pinia 状态 (auth、chat、ai、notification、theme、adminAuth)
│   │   ├── views/                    # 页面
│   │   │   ├── auth/                 # 登录、注册、忘记密码
│   │   │   ├── chat/                 # 聊天主界面
│   │   │   ├── admin/                # 管理后台 (仪表盘、用户/消息/群组/AI/通知/配置/日志)
│   │   │   ├── landing/              # 官网首页 (Hero、架构、技术栈、能力、AI Demo、快速开始)
│   │   │   └── profile/              # 个人资料
│   │   ├── styles/                   # 全局样式
│   │   ├── types/                    # TypeScript 类型定义
│   │   └── utils/                    # 工具函数 (请求封装、格式化、通知、Toast)
│   ├── .env.example                  # 环境变量模板
│   └── package.json
│
└── .gitignore
```

---

## 快速开始

### 环境要求

| 依赖 | 最低版本 | 说明 |
|------|---------|------|
| JDK | 17 | 后端运行环境 |
| Node.js | 18 | 前端构建环境 |
| MySQL | 8.0 | 主数据库 |
| Redis | 6.0 | 缓存与分布式锁 |
| RabbitMQ | 3.12 | 消息队列 |
| MinIO | 最新 | 对象存储 |
| Ollama | 最新 | 本地 AI 推理（可选，需拉取 deepseek-r1:8b 模型） |
| Maven | 3.8 | 后端依赖管理 |

### 后端启动

1. **初始化数据库**

   ```bash
   mysql -u root -p < backend/src/main/resources/db/schema.sql
   ```

2. **配置环境变量**

   ```bash
   cp backend/.env.example backend/.env
   ```

   编辑 `backend/.env`，填入你的 MySQL、Redis、RabbitMQ、MinIO、邮箱授权码、JWT 密钥及 AI 服务配置。

3. **（可选）生成开发环境 SSL 证书**

   开发环境默认启用 HTTPS，需使用 mkcert 生成本地证书：

   ```bash
   mkcert chatvibe.icu localhost 127.0.0.1 ::1
   ```

   将生成的 `.pem` 文件放入 `backend/src/main/resources/ssl/` 目录（证书文件已被 .gitignore 忽略）。

4. **启动后端服务**

   ```bash
   cd backend
   mvn spring-boot:run
   ```

   后端默认运行在 `https://localhost:8080`。

### 前端启动

1. **安装依赖**

   ```bash
   cd frontend
   npm install
   ```

2. **（可选）生成前端开发证书**

   前端开发环境同样使用 HTTPS，将 mkcert 生成的证书放入 `frontend/ssl/` 目录，并在 `.env.development` 中启用 `SSL_ENABLED=true`。

3. **启动开发服务器**

   ```bash
   npm run dev
   ```

4. **构建生产版本**

   ```bash
   npm run build
   ```

   构建产物输出到 `frontend/dist/`。

---

## 环境配置

### 后端环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` | MySQL 连接配置 | localhost / 3306 / chatvibe |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DB` | Redis 连接配置 | localhost / 6379 / - / 5 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | RabbitMQ 连接配置 | - |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` | MinIO 对象存储配置 | http://localhost:9000 / - / - / chatvibe |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | 163 邮箱 SMTP 配置（密码为授权码） | - |
| `JWT_SECRET` | JWT 签名密钥（至少 256 bits） | 开发环境默认值 |
| `AI_PROVIDER` | AI 供应商：`qwen` 或 `ollama` | ollama |
| `AI_FAILOVER_ENABLED` | 是否启用多供应商故障转移 | true |
| `QWEN_API_KEY` / `QWEN_MODEL` | 阿里云百炼 API Key 与模型名 | - / qwen3.6-flash |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | Ollama 本地服务地址与模型名 | http://localhost:11434 / deepseek-r1:8b |
| `SSL_ENABLED` / `SSL_CERT_PATH` / `SSL_KEY_PATH` | 开发环境 HTTPS 证书配置 | true / classpath:ssl/... |

完整配置项见 `backend/.env.example`。

### 前端环境变量

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE` | 后端 API 地址（开发环境：https://localhost:8080/api） |
| `VITE_WS_URL` | WebSocket 地址（开发环境：https://localhost:8080/ws-chat） |
| `SSL_ENABLED` / `SSL_CERT_PATH` / `SSL_KEY_PATH` | 前端开发环境 HTTPS 证书 |

完整配置项见 `frontend/.env.example`。

---

## 数据库设计

数据库包含 13 张表，统一使用 utf8mb4 字符集，逻辑删除字段 `deleted`（0-未删除，1-已删除）。所有涉及唯一约束的表（user、conversation_member、group_member、message_hidden）采用 MySQL 生成列（Generated Column）实现软删除后唯一约束仍可复用——删除记录时生成列置 NULL，不再参与唯一索引。

| 表名 | 说明 |
|------|------|
| `user` | 用户表，邮箱即账号，密码 BCrypt 加密，含通知偏好与登录版本号 |
| `conversation` | 会话表，私聊(1)/群聊(2)/AI(3)统一模型，记录最后消息摘要 |
| `conversation_member` | 会话成员表，记录角色、未读数、免打扰、置顶状态 |
| `message` | 消息表，复合索引 (conversation_id, created_at) 优化历史查询 |
| `message_hidden` | 消息隐藏表，用户级消息删除（仅对操作者隐藏） |
| `friend_request` | 好友请求表，状态：待处理/已接受/已拒绝 |
| `group_member` | 群组成员表，冗余于会话成员表用于群组维度管理 |
| `ai_conversation` | AI 会话表，存储 ChatMemory 上下文（20 条消息窗口） |
| `notification` | 通知表，8 种通知类型，复合索引优化未读查询 |
| `operation_log` | 操作日志表，记录管理员操作（20 种类型），含操作者、IP、详情 |
| `announcement` | 公告表，管理员发布的系统公告，支持全员/指定用户、撤回 |
| `ai_provider` | AI 供应商配置表，管理员后台动态管理供应商（名称、模型、密钥、优先级） |
| `system_config` | 系统配置表，键值对存储限流/熔断/邮件等运行时参数（JSON 格式） |

---

## AI 模块

AI 模块基于 **Spring AI 1.0.0** 实现，通过 OpenAI 兼容接口统一调用阿里云百炼 (Qwen) 和本地 Ollama。

### 多供应商故障转移

```
开发环境 (dev):  Ollama 本地  →  Qwen 云端兜底
生产环境 (prod): Qwen 云端    →  Ollama 本地兜底
```

`FailoverAiService` 按优先级逐个尝试供应商，通过 `@ConditionalOnProperty` 激活，单供应商模式退回 `SpringAiChatService`。

### SSE 流式回复

AI 对话通过 `/chat` 接口返回 `SseEmitter`，流式推送过程包含：

- **Redis 手动限流**：在创建 SseEmitter 之前执行，避免响应头已提交后无法返回限流错误
- **resilience4j-reactor 熔断**：在 `Flux<String>` 上使用 Reactor 操作符实现流级别熔断，`@CircuitBreaker` 注解无法捕获 `Flux.subscribe()` 阶段的异步错误
- **Markdown 清理**：AI 回复经过两轮清洗（全量 Markdown 模式移除 + 残余符号剥离），确保流式输出和最终展示均为纯文本
- **消息分段**：AI 回复按 `\n\n` 分段存储为独立消息，前端使用占位消息 ID（`ai_msg_*`）进行去重替换

### 上下文管理

采用 Spring AI `ChatMemory` + `JdbcChatMemoryRepository` 管理多轮对话上下文，对话 ID 遵循命名规则：

- `chat-{chatConversationId}`：群聊共享上下文
- `ai-{aiConversationId}`：独立 AI 对话上下文

---

## 管理后台

管理后台采用独立的认证体系与前端路由（`/admin`），后端通过 Spring Security 的 `hasAnyRole("SUPER_ADMIN", "ADMIN", "OPERATOR")` 进行接口级权限控制，前端使用独立的 admin token 与路由守卫。

### 角色权限

| 角色 | 权限 |
|------|------|
| `SUPER_ADMIN` | 全部功能，含系统配置修改 |
| `ADMIN` | 用户/消息/群组/AI/公告管理 |
| `OPERATOR` | 数据概览、消息审计、通知公告查看 |

### 功能模块

```
┌──────────────────────────────────────────────────────┐
│                  管理后台 (Vue 3 + Element Plus)        │
│  ┌────────┬──────────────────────────────────────┐   │
│  │ 侧边栏  │  数据概览  用户管理  消息审计  群组管理 │   │
│  │ 导航    │  AI 服务  通知公告  系统配置  操作日志 │   │
│  └────────┴──────────────────────────────────────┘   │
│         独立 Token · 亮/暗主题 · ECharts 图表          │
└───────────────────────┬──────────────────────────────┘
                        │ HTTP (独立 admin token)
┌───────────────────────┴──────────────────────────────┐
│              Admin API (Spring Security RBAC)          │
│  Dashboard · User · Message · Group · AI               │
│  Announcement · Notification · Config · Log            │
└──────────────────────────────────────────────────────┘
```

### 关键设计

- **独立认证**：管理后台使用独立的 JWT token（存储于 `chatvibe_admin_token`），与 C 端 token 完全隔离，路由守卫拦截未授权访问
- **动态配置热更新**：限流器/熔断器参数修改后直接写入 `system_config` 表，并同步更新 Resilience4j 运行时注册表，无需重启；邮件配置通过 `DynamicMailSenderProvider` 按需创建 `JavaMailSenderImpl`，支持管理后台实时修改 SMTP 参数
- **公告异步推送**：公告发布通过 RabbitMQ 异步处理，支持全员/指定用户两种范围；撤回公告时自动删除已分发的 C 端通知记录
- **仪表盘实时监控**：AI 调用指标通过 `AiCallMetricsService` 在 Redis 中实时记录，仪表盘每 60 秒自动刷新指标、每 30 秒刷新健康状态；ECharts 图表通过 CSS 变量自动适配亮/暗主题
- **操作审计全覆盖**：20 种操作类型（登录/登出、用户封禁/解封/角色变更/密码重置、消息删除、群组解散/转让、公告发布/撤回、限流/熔断配置、缓存清除、AI 供应商管理、故障转移配置、邮件配置）均自动记录操作者、IP、详情

---

## 工程实践

### 并发安全

- 所有写操作使用 Redis 分布式锁（好友/群组操作 5 秒 TTL，标记已读/重新加入 3 秒 TTL）
- 私聊会话创建使用有序 minId:maxId 锁键，防止 A→B 和 B→A 创建重复会话
- 免打扰/置顶切换使用 SQL `CASE WHEN` 实现原子状态反转，防止并发翻转

### 性能优化

- 会话列表查询使用 LEFT JOIN 替代 4 个关联子查询，并附加 `LIMIT 200` 防止过量返回
- 好友列表、好友请求列表、群成员列表使用批量 IN 查询消除 N+1 问题
- 消息表复合索引 `(conversation_id, created_at)` 覆盖历史消息分页查询，避免回表排序
- 数据库连接执行 `SET time_zone = '+08:00'`，对齐 MySQL `NOW()` 与 Java `LocalDateTime.now()`

### 限流与熔断

Resilience4j 覆盖全部 API 接口：

- **RateLimiter**：按接口分级配置，从 5 次/小时（修改密码）到 120 次/分钟（通知未读数）不等
- **CircuitBreaker**：滑动窗口 10 次请求，失败率 50% 触发熔断，30-60 秒后半开试探
- **BusinessException 排除**：业务异常不计入熔断失败率，避免正常业务拒绝触发误判

---

## 测试

后端使用 JUnit 5 + Spring Boot Test + H2 内存数据库进行集成测试，JaCoCo 统计代码覆盖率。

```bash
cd backend
mvn test
```

覆盖率报告输出到 `backend/target/site/jacoco/`，对 `auth`、`user`、`security` 模块设置了 90% 行覆盖率门槛。

---

## License

本项目采用 [MIT License](LICENSE) 开源协议。
