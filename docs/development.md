# 开发说明

## Android

使用 Android Studio 打开 `mobile/`。当前客户端使用 Kotlin、Jetpack Compose、Material 3 和 AndroidX ViewModel。

模拟器访问本机 FastAPI 使用：

```text
http://10.0.2.2:8000
```

真机调试需要把地址改成开发电脑在同一局域网内的 IP，并允许防火墙访问 8000 端口。

当前服务器联调地址为：

```text
https://dnsgo.xyz/todocloud-api/api/v1
```

服务器通过 Nginx HTTPS 入口转发 TodoCloud API。容器端口 `18000 -> 8000` 现在只监听服务器本机，公网不能直连；现有 traffic-detection 的 API 路由和端口未修改。

Android 任务页同时提供“从截图识别任务”和“语音/文字提取任务”。后者先使用设备系统语音识别生成可编辑文字，再调用 `/ai/parse-text`；设备没有语音服务或用户不授予录音权限时，可直接输入文字。

## 服务端

服务端要求 Python 3.12。首次运行：

```powershell
cd server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e .
uvicorn app.main:app --reload
```

## Docker 服务

```powershell
docker compose up -d postgres redis minio
docker compose ps
```

MinIO 控制台默认地址为 `http://localhost:9001`，开发环境账号密码来自 `docker-compose.yml`，正式部署前必须修改。

## 开发阶段顺序

1. 完成登录、刷新令牌和用户资料。
2. 完成任务增删改查和 Android 云端同步。
3. 完成日历视图和本地系统通知。
4. 配置 `AI_API_KEY` 后联调截图视觉解析和聊天时间解析，再完善异步重试。
5. 完善多设备同步、推送通知和生产运维。
