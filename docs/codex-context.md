# TodoCloud 项目交接记录

> 本文件用于在新的 Codex 会话中恢复项目上下文。不要在这里写入密码、私钥、验证码、API Key 或其他敏感凭据。

## 项目定位

TodoCloud 是一个 Android 待办事项应用，目标功能包括：

- 云端账号登录与多设备同步
- 任务增删改查、日历视图
- 可自定义提前提醒，并唤醒 Android 系统通知
- 上传截图，由 AI 根据截图和聊天记录创建待办
- 根据聊天记录中的时间，以及用户时区解析任务时间

当前只开发 Android 客户端，服务端作为独立项目维护。

## 技术栈与目录

- Android：Kotlin、Jetpack Compose、Material 3、AndroidX ViewModel
- API：FastAPI、Python 3.12
- 数据库：PostgreSQL 16
- 缓存与队列：Redis 7、Celery
- 对象存储：MinIO
- 部署：Docker Compose

```text
D:\L\Study\TodoList
├── mobile/                  # Android 客户端
├── server/                  # FastAPI 服务端
├── docs/                    # 架构、接口、数据模型和交接文档
└── docker-compose.yml       # 本地/服务器基础设施编排
```

## 已完成

### 本地项目

- 已创建 Android 和 FastAPI 两个项目骨架。
- Android 已包含 Compose 应用入口、Material 3 主题、登录/注册界面、任务列表和任务编辑操作。
- Android 已接入 OkHttp API 客户端、HTTPS API 地址和 Android Keystore 加密会话保存，支持创建、完成、删除和刷新任务。
- Android 已支持设置截止日期/时间、提前分钟数提醒，并通过 AlarmManager 唤醒系统通知。
- Android 已加入可切换月份的日历页，支持按日期查看任务、通知渠道和 Android 13 通知权限请求。
- 服务端已实现用户注册、登录、当前用户和任务 CRUD API。
- 任务 API 已保存 `reminder_offset_minutes`，服务启动时会自动补齐该字段。
- 服务端使用 PBKDF2-SHA256 保存密码哈希；短期 HMAC access token 配合哈希保存、轮换和可撤销的 refresh token。
- 登录、注册、刷新令牌和 AI 解析接口已通过 Redis 做基础限流。
- 服务启动时会自动创建当前 MVP 的 `users` 和 `tasks` 表。
- 已编写以下文档：
  - `docs/architecture.md`
  - `docs/api-contract.md`
  - `docs/data-model.md`
  - `docs/development.md`
- Docker Compose 已配置 PostgreSQL、Redis、MinIO、FastAPI server 和 Celery worker。
- Python 服务端代码已通过编译检查。
- Android Debug APK 已成功构建：
  `mobile/app/build/outputs/apk/debug/app-debug.apk`
- 已完成认证与任务 API 的服务器集成测试，测试数据已清理。
- 已完成提醒字段的服务器远程集成测试，包含创建、读取、清除和删除流程。
- 已完成服务器增量部署，TodoCloud API 健康检查通过。
- 已接入截图 AI 候选流程：图片保存到 MinIO，视觉模型返回候选任务，Android 端确认后才创建任务。
- 已接入文字 AI 候选流程：Android 端支持系统 `SpeechRecognizer` 免费转写（录音不上传），转写结果可编辑后调用 `/ai/parse-text`，与截图解析共用时间理解、去重和确认创建流程。
- Android 任务页已增加“语音/文字提取任务”入口，设备无语音服务或拒绝录音权限时仍可直接输入聊天文字。
- Docker Compose 配置已通过配置检查。

### Git 状态

- 当前分支：`main`
- 当前工作区在本次记录前是干净的。
- 最近提交包括 Android 构建修复、生产 Compose 配置、交接文档和测试 APK。
- 本机 GitHub SSH 身份验证已成功。
- GitHub 仓库为 `git@github.com:fastbit-ljb/todocloud.git`，当前 `main` 已推送。
- `server/.env` 被 `.gitignore` 忽略，不得提交；不要把密钥写入项目。

### 服务器连接

- ECS SSH 免密连接已验证成功：`root@dnsgo.xyz:22`
- 服务器 Docker 版本：`29.1.3`
- 服务器系统运行状态检查成功。
- TodoCloud 已部署到 `/opt/todocloud`，使用 Docker 内部网络运行 server、worker、PostgreSQL、Redis 和 MinIO。
- API 容器现在以 `127.0.0.1:18000 -> 8000` 监听，仅由 Nginx HTTPS 路径 `/todocloud-api/` 转发；原有 `traffic-detection` 的 `127.0.0.1:8000` 未修改。
- Android 生产联调地址为 `https://dnsgo.xyz/todocloud-api/api/v1`，公网不再直连 TodoCloud 的 18000 端口。
- Nginx 已完成配置检查并重载，HTTPS 健康检查通过；没有修改 traffic-detection 的既有路由。
- 服务器内部健康检查、认证和任务 CRUD 集成测试已通过。
- 本机 SSH 私钥已存在并由 OpenSSH 使用；不要读取、显示、复制或上传私钥内容。

## 未完成事项

### 产品与服务端

- 设备会话列表、更细的权限控制和 refresh token 的后台清理任务。
- 数据库 Alembic 迁移、分页、筛选和冲突处理。
- 多条提醒规则、推送提醒和服务端提醒任务。
- MinIO 预签名 URL、AI 异步任务队列和失败重试。
- Celery 异步任务、重试、幂等和失败状态处理。
- 语音转写目前依赖手机系统语音服务；不同厂商可用性和离线能力不同，后续如需稳定离线转写再单独评估引入模型或服务。
- 数据库 ORM 模型、迁移脚本和测试。

### 部署与运维

- 为服务器补充备份、日志轮转和监控告警。
- 清理阿里云安全组中不再需要的公网 TCP 18000 规则。
- 配置移动端 release API 地址和发布签名版本。
- 暂未决定是否使用独立部署用户替代 root 进行日常发布。

## 风险与注意事项

- `docker-compose.yml` 仍包含开发环境占位密码，生产部署必须使用服务器上的独立 `.env` 和生产覆盖文件。
- Android 当前通过 HTTPS 域名访问 API；Debug 构建仍保留本地开发用的明文配置能力，正式 release 不应启用明文地址。
- 截图 AI 使用服务器 `.env` 中的 `AI_API_KEY`；密钥不进入 GitHub 或 APK，上传解析接口只返回候选任务，仍需用户确认后创建。
- PostgreSQL、Redis、MinIO 和 API 的端口不能未经评估就直接暴露到公网，生产环境应使用内网、反向代理或防火墙限制。
- 当前认证已具备短期 access token、refresh token 轮换/撤销和基础速率限制；审计日志、备份和更细权限控制尚未实现。
- AI 解析结果必须先让用户确认，再写入正式任务；不能默认自动创建不可撤销的数据。
- 时间解析必须保存用户 IANA 时区，并明确以聊天记录时间作为相对时间基准。
- 生产部署前需要配置 HTTPS、密钥轮换、备份恢复演练和最小权限账号。
- `/opt/traffic_detection_core` 与 Label Studio 属于另一个项目，禁止在 TodoCloud 任务中修改该目录。

## 建议下一步

1. 把当前 API 地址配置抽离为构建变量，区分模拟器、本地真机和生产环境，并准备 release 签名。
2. 配置 `AI_API_KEY` 并联调真实视觉模型，再加入异步队列、重试和限流。
3. 完成 refresh token 清理任务、Alembic 迁移和 API 自动化测试。
4. 增加多条提醒规则、推送通知和多设备同步冲突处理。
5. 删除阿里云安全组中不再需要的公网 TCP 18000 规则，并补充备份恢复演练。

## 新会话启动提示词

```text
继续开发 TodoCloud。请先读取：
D:\L\Study\TodoList\README.md
D:\L\Study\TodoList\docs\codex-context.md
D:\L\Study\TodoList\docs\architecture.md
D:\L\Study\TodoList\docs\development.md

当前项目是 Android + FastAPI + PostgreSQL + Redis + MinIO。
服务器 SSH 为 root@dnsgo.xyz，已配置本机免密登录；不要读取、显示或上传私钥。
不要操作 /opt/traffic_detection_core，那是另一个项目。
先检查当前工作区和 Git 状态，再继续我指定的任务。
```
