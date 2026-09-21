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
- Android 已包含 Compose 应用入口、Material 3 主题和底部导航占位页面。
- 服务端已包含 FastAPI 应用入口、健康检查接口、配置、数据库会话和 Celery 基础结构。
- 已编写以下文档：
  - `docs/architecture.md`
  - `docs/api-contract.md`
  - `docs/data-model.md`
  - `docs/development.md`
- Docker Compose 已配置 PostgreSQL、Redis、MinIO、FastAPI server 和 Celery worker。
- Python 服务端代码已通过编译检查。
- Android Debug APK 已成功构建：
  `mobile/app/build/outputs/apk/debug/app-debug.apk`
- Docker Compose 配置已通过配置检查。

### Git 状态

- 当前分支：`main`
- 当前工作区在本次记录前是干净的。
- 最近提交：
  - `7142874 fix: make Android project build with AGP 9`
  - `a186964 chore: initialize TodoCloud Android and server projects`
- 本机 GitHub SSH 身份验证已成功。
- 目标 GitHub 仓库 `fastbit-ljb/todocloud` 当时尚未创建，因此项目尚未成功推送到 GitHub。
- 创建空的 GitHub 仓库后，再添加远程地址并推送；不要把密钥写入项目。

### 服务器连接

- ECS SSH 免密连接已验证成功：`root@dnsgo.xyz:22`
- 服务器 Docker 版本：`29.1.3`
- 服务器系统运行状态检查成功。
- 目前只做过 SSH 连通性和只读状态检查，TodoCloud 尚未部署到服务器。
- 本机 SSH 私钥已存在并由 OpenSSH 使用；不要读取、显示、复制或上传私钥内容。

## 未完成事项

### 产品与服务端

- 注册、登录、刷新令牌和用户资料。
- 任务 CRUD、任务状态、排序及云端同步。
- 日历查询接口和 Android 日历视图。
- 自定义提醒规则及服务端同步。
- Android 本地通知调度和通知权限处理。
- MinIO 附件上传、预签名 URL 和附件记录。
- OCR/视觉模型接入，以及聊天记录时间解析。
- Celery 异步任务、重试、幂等和失败状态处理。
- 数据库 ORM 模型、迁移脚本和测试。

### 部署与运维

- 创建 GitHub 仓库并推送当前代码。
- 为服务器创建生产环境配置，不使用 Compose 中的开发默认密码。
- 在服务器部署 `/opt/todocloud`，配置 Docker Compose、数据卷、日志和健康检查。
- 配置域名、HTTPS、反向代理和必要的阿里云安全组规则。
- 暂未决定是否使用独立部署用户替代 root 进行日常发布。

## 风险与注意事项

- `docker-compose.yml` 当前包含开发环境占位密码，禁止直接用于公网生产环境。
- PostgreSQL、Redis、MinIO 和 API 的端口不能未经评估就直接暴露到公网，生产环境应使用内网、反向代理或防火墙限制。
- 当前认证、权限、速率限制、审计日志和数据备份尚未实现。
- AI 解析结果必须先让用户确认，再写入正式任务；不能默认自动创建不可撤销的数据。
- 时间解析必须保存用户 IANA 时区，并明确以聊天记录时间作为相对时间基准。
- 生产部署前需要配置 HTTPS、密钥轮换、备份恢复演练和最小权限账号。
- `/opt/traffic_detection_core` 与 Label Studio 属于另一个项目，禁止在 TodoCloud 任务中修改该目录。

## 建议下一步

1. 创建空的 GitHub 仓库 `fastbit-ljb/todocloud` 并推送当前代码。
2. 先完成 FastAPI 的认证、用户和任务 CRUD，以及数据库模型和迁移。
3. 在本地通过 Docker Compose 联调 PostgreSQL、Redis、MinIO 和 API。
4. 完成 Android 登录、任务列表、任务编辑和同步。
5. 加入日历和本地系统通知，再实现自定义提前提醒。
6. 最后接入截图上传、OCR/视觉模型和聊天时间解析，并增加用户确认流程。
7. 通过独立生产配置部署到服务器，部署前先备份并确认域名、HTTPS 和安全组策略。

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
