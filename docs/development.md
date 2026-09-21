# 开发说明

## Android

使用 Android Studio 打开 `mobile/`。当前客户端使用 Kotlin、Jetpack Compose、Material 3 和 AndroidX ViewModel。

模拟器访问本机 FastAPI 使用：

```text
http://10.0.2.2:8000
```

真机调试需要把地址改成开发电脑在同一局域网内的 IP，并允许防火墙访问 8000 端口。

当前服务器 Debug 联调地址为：

```text
http://43.108.38.230:18000/api/v1
```

服务器通过 `18000 -> 容器 8000` 提供 TodoCloud API，真机测试前需要在阿里云安全组放行 TCP 18000。该地址仅用于 Debug，正式版本必须切换到 HTTPS 域名。

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
4. 完成自定义提醒和多设备同步。
5. 接入截图 OCR、视觉模型和聊天时间解析。
