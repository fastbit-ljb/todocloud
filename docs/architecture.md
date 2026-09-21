# 项目架构

## 总体结构

```text
Android App
    │ HTTPS / JSON REST API
    ▼
FastAPI Server
    ├── PostgreSQL：业务数据和任务状态
    ├── Redis：缓存、队列、分布式锁
    ├── MinIO：截图和附件
    └── Celery Worker：AI 解析、耗时任务、提醒调度
```

## Android 客户端

Android 端使用 Kotlin 和 Jetpack Compose。客户端负责：

- 登录状态和用户交互
- 任务、日历和提醒的展示
- 图片选择、截图上传
- 本地缓存和离线草稿
- 使用 Android 系统通知提醒用户
- 与服务端同步任务

客户端不会直接访问 PostgreSQL、Redis 或 MinIO。附件上传先向服务端申请预签名地址，再把文件上传到 MinIO。

## 服务端

FastAPI 负责认证、任务、日历、提醒和文件上传接口。PostgreSQL 是业务数据的唯一事实来源，Redis 用于缓存和异步队列，MinIO 保存图片及附件。

截图识别流程如下：

```text
客户端上传截图
    ↓
服务端生成上传地址并保存附件记录
    ↓
Celery Worker 读取截图并执行 OCR / 视觉模型解析
    ↓
以聊天记录时间和用户时区解析相对时间
    ↓
返回待确认任务
    ↓
用户确认后写入 tasks 和 task_reminders
```

## 时间规则

数据库统一使用 UTC 时间保存 `timestamptz`。用户资料保存 IANA 时区，例如 `Asia/Shanghai`。解析“明天晚上八点”时，以截图中聊天记录的时间作为基准，再结合用户时区换算成明确的 UTC 时间。

## 通知规则

本地通知负责精确提醒，服务端负责提醒配置同步和多设备同步。每次任务或提醒规则变化后，客户端重新安排本地通知；服务端可以通过 FCM 发送跨设备更新事件。

