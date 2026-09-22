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
- Android 已接入 OkHttp API 客户端和本地登录 token 保存，支持创建、完成、删除和刷新任务。
- Android 已支持设置截止日期/时间、提前分钟数提醒，并通过 AlarmManager 唤醒系统通知。
- Android 已加入可切换月份的日历页，支持按日期查看任务、通知渠道和 Android 13 通知权限请求。
- 服务端已实现用户注册、登录、当前用户和任务 CRUD API。
- 任务 API 已保存 `reminder_offset_minutes`，服务启动时会自动补齐该字段。
- 服务端使用 PBKDF2-SHA256 保存密码哈希，使用 HMAC 签名 token 做当前阶段认证。
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
- API 容器现在发布 `18000 -> 8000`，用于真机 Debug；原有 `traffic-detection` 的 `127.0.0.1:8000` 未修改。
- 真机测试需要在阿里云安全组放行 TCP 18000；未修改 Nginx 或其他项目。
- 服务器内部健康检查、认证和任务 CRUD 集成测试已通过。
- 本机 SSH 私钥已存在并由 OpenSSH 使用；不要读取、显示、复制或上传私钥内容。

## 未完成事项

### 产品与服务端

- refresh token、设备会话和更细的权限控制。
- 数据库 Alembic 迁移、分页、筛选和冲突处理。
- 多条提醒规则、推送提醒和服务端提醒任务。
- MinIO 附件上传、预签名 URL 和附件记录。
- OCR/视觉模型接入，以及聊天记录时间解析。
- Celery 异步任务、重试、幂等和失败状态处理。
- 数据库 ORM 模型、迁移脚本和测试。

### 部署与运维

- 为服务器补充备份、日志轮转和监控告警。
- 在不修改安全组端口的前提下，评估使用现有 HTTPS 反向代理提供移动端 API。
- 配置移动端生产 API 地址和发布签名版本。
- 暂未决定是否使用独立部署用户替代 root 进行日常发布。

## 风险与注意事项

- `docker-compose.yml` 仍包含开发环境占位密码，生产部署必须使用服务器上的独立 `.env` 和生产覆盖文件。
- 当前 Android Debug 使用 HTTP 公网地址，仅用于测试；正式发布必须使用 HTTPS 域名和 release 配置。
- PostgreSQL、Redis、MinIO 和 API 的端口不能未经评估就直接暴露到公网，生产环境应使用内网、反向代理或防火墙限制。
- 当前认证、权限、速率限制、审计日志和数据备份尚未实现。
- AI 解析结果必须先让用户确认，再写入正式任务；不能默认自动创建不可撤销的数据。
- 时间解析必须保存用户 IANA 时区，并明确以聊天记录时间作为相对时间基准。
- 生产部署前需要配置 HTTPS、密钥轮换、备份恢复演练和最小权限账号。
- `/opt/traffic_detection_core` 与 Label Studio 属于另一个项目，禁止在 TodoCloud 任务中修改该目录。

## 建议下一步

1. 把当前 API 地址配置抽离为构建变量，区分模拟器、本地真机和生产环境。
2. 完成 refresh token、Alembic 迁移和 API 自动化测试。
3. 实现 MinIO 附件上传、截图解析和 AI 候选任务确认流程。
4. 增加月视图日历、推送提醒和多设备同步冲突处理。
5. 在不改动现有安全组端口的前提下，通过现有 HTTPS 入口提供移动端 API。

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
