# TodoCloud

TodoCloud 是一个 Android 待办事项应用，支持云端登录、任务同步、日历、可自定义提醒，以及通过截图和聊天记录自动生成任务。

当前项目拆分为两个独立部分：

- `mobile/`：Kotlin + Jetpack Compose Android 客户端
- `server/`：FastAPI 云端服务

基础设施使用 PostgreSQL、Redis 和 MinIO，统一通过 Docker Compose 启动。

## 当前状态

这是项目第一版骨架，已经包含：

- Android Compose 应用入口和底部导航占位页面
- FastAPI 健康检查接口
- PostgreSQL、Redis、MinIO 和 API 服务的 Docker Compose 配置
- 项目架构、API 约定、数据模型和本地开发说明

## 目录

```text
TodoCloud/
├── mobile/                  # Android 客户端
├── server/                  # FastAPI 服务端
├── docs/                    # 架构与开发文档
└── docker-compose.yml       # 本地基础设施
```

## 快速开始

### 启动服务端基础设施

```powershell
Copy-Item server/.env.example server/.env
docker compose up -d postgres redis minio
```

### 启动 FastAPI

```powershell
cd server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e .
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

健康检查地址：`http://localhost:8000/api/v1/health`。

### 打开 Android 项目

使用 Android Studio 打开 `mobile/`，等待 Gradle 同步后运行 `app`。开发机上的服务地址在 Android 模拟器中通常使用 `http://10.0.2.2:8000`，真机调试时改成电脑局域网 IP。

更多说明见：

- [项目架构](docs/architecture.md)
- [API 约定](docs/api-contract.md)
- [数据模型](docs/data-model.md)
- [开发说明](docs/development.md)

