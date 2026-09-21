# 数据模型

## users

保存账号、密码摘要、显示名称、默认时区和创建时间。

## refresh_tokens

保存刷新令牌摘要、所属用户、设备信息、过期时间和撤销状态。数据库不保存明文刷新令牌。

## tasks

核心字段：

```text
id             UUID
user_id        UUID
title          VARCHAR
description    TEXT
status         todo | in_progress | done | archived
priority       low | normal | high | urgent
due_at         TIMESTAMPTZ nullable
timezone       VARCHAR
source         manual | ai_screenshot | import
created_at     TIMESTAMPTZ
updated_at     TIMESTAMPTZ
```

## task_reminders

保存任务的提醒规则和执行状态：

```text
task_id        UUID
offset_minutes INTEGER nullable
remind_at      TIMESTAMPTZ nullable
channel        local | push | both
enabled        BOOLEAN
last_sent_at   TIMESTAMPTZ nullable
```

## attachments

保存 MinIO 的对象键、文件名、MIME 类型、大小和所属用户。图片内容本身不放进 PostgreSQL。

## ai_parse_records

保存原始截图附件、识别状态、模型输出、基准时间、解析出的候选任务和用户确认结果，方便重试与审计。

## device_tokens

保存 Android 设备的 FCM Token、设备名称、应用版本和最后活跃时间，用于多设备同步。

