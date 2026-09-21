# 数据模型

## 当前已实现

### users

保存账号、密码摘要、显示名称、默认时区和创建时间。

```text
id             INTEGER primary key
email          VARCHAR(320) unique
password_hash  VARCHAR(512)
display_name   VARCHAR(80) nullable
timezone       VARCHAR(64)
created_at     TIMESTAMPTZ
```

### tasks

```text
id          INTEGER primary key
user_id     INTEGER -> users.id
title       VARCHAR(200)
description TEXT nullable
due_at      TIMESTAMPTZ nullable
completed   BOOLEAN
created_at  TIMESTAMPTZ
updated_at  TIMESTAMPTZ
```

服务启动时会为当前 MVP 自动创建缺失表。生产环境正式发布前需要切换到 Alembic 迁移，避免仅依赖 `create_all`。

## 规划中的模型

### refresh_tokens

保存刷新令牌摘要、所属用户、设备信息、过期时间和撤销状态。数据库不保存明文刷新令牌。

### task_reminders

```text
task_id        INTEGER
offset_minutes INTEGER nullable
remind_at      TIMESTAMPTZ nullable
channel        local | push | both
enabled        BOOLEAN
last_sent_at   TIMESTAMPTZ nullable
```

### attachments

保存 MinIO 的对象键、文件名、MIME 类型、大小和所属用户。图片内容本身不放进 PostgreSQL。

### ai_parse_records

保存原始截图附件、识别状态、模型输出、基准时间、解析出的候选任务和用户确认结果，方便重试与审计。

### device_tokens

保存 Android 设备的 FCM Token、设备名称、应用版本和最后活跃时间，用于多设备同步。
