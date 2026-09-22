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
id                       INTEGER primary key
user_id                  INTEGER -> users.id
title                    VARCHAR(200)
description              TEXT nullable
due_at                   TIMESTAMPTZ nullable
reminder_offset_minutes  INTEGER nullable
completed                BOOLEAN
created_at               TIMESTAMPTZ
updated_at               TIMESTAMPTZ
```

服务启动时会为当前 MVP 自动创建缺失表，并为已有数据库补齐提醒字段。生产环境正式发布前需要切换到 Alembic 迁移，避免仅依赖启动时迁移。

### attachments

保存截图在 MinIO 中的对象键和元数据，图片内容本身不放入 PostgreSQL。

```text
id             INTEGER primary key
user_id        INTEGER -> users.id
object_key     VARCHAR(512) unique
original_name  VARCHAR(255)
content_type   VARCHAR(120)
size_bytes     INTEGER
created_at     TIMESTAMPTZ
```

### ai_parse_records

保存截图解析状态、候选任务 JSON 和错误信息。候选任务必须经过客户端确认后才写入 `tasks`。

```text
id              INTEGER primary key
user_id         INTEGER -> users.id
attachment_id   INTEGER -> attachments.id
status          VARCHAR(32)
candidates_json TEXT nullable
error_message   TEXT nullable
created_at      TIMESTAMPTZ
```

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

### device_tokens

保存 Android 设备的 FCM Token、设备名称、应用版本和最后活跃时间，用于多设备同步。
