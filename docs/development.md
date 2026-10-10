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

### Release 签名

`assembleRelease` 从 `mobile/keystore.properties` 读取签名配置（该文件已被 `.gitignore` 忽略，不要提交）：

```properties
storeFile=C:/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

没有这个文件时仍能构建，但产出的是未签名的 `app-release-unsigned.apk`。正式版必须继续使用与旧版相同的证书（当前为 Android Debug 证书，SHA-256 `8af07bcd5ca4f95d9877d0b54fdd472b96aff91dd465f4de9f51261637938aec`），否则已安装的用户无法覆盖升级。签名后的 APK 位于 `mobile/app/build/outputs/apk/release/app-release.apk`，可用 `apksigner verify --print-certs` 核对证书。

## 服务端

服务端要求 Python 3.12。首次运行：

```powershell
cd server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e .
uvicorn app.main:app --reload
```

### 数据库迁移（Alembic）

表结构由 `server/migrations/` 管理，服务启动时会自动执行 `alembic upgrade head`（`app/db/migrate.py`，带 PostgreSQL advisory lock，多实例同时启动也只会执行一次）。`0001_baseline` 对应 2026-10 线上的表结构，并且是幂等的：在已有数据库上只登记版本号，不改结构。

修改 `app/db/models.py` 后必须新增迁移，否则 CI 里的 `test_models_match_migrated_schema` 会失败：

```powershell
cd server
alembic revision -m "add xxx column"   # 在生成的文件里写 op.add_column(...) 等
alembic upgrade head
```

线上部署包含迁移时，先在 `/opt/todocloud/backups/` 下用 `pg_dump` 做完整备份再更新。

### 服务端测试

```powershell
cd server
pip install -e . pytest httpx
python -m pytest -q
```

依赖数据库的测试需要 `TEST_DATABASE_URL`（库名必须以 `_test` 结尾，测试会清空它的 `public` schema）和可用的 Redis，没有时自动跳过；CI 会用临时的 PostgreSQL 和 Redis 全部跑一遍。

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
