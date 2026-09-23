# API 约定

API 前缀为 `/api/v1`，请求和响应使用 JSON，除文件上传接口外均使用 UTF-8。

## 当前已实现：认证

```text
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /auth/me
```

注册请求：

```json
{
  "email": "user@example.com",
  "password": "至少 8 位",
  "display_name": "可选昵称",
  "timezone": "Asia/Shanghai"
}
```

登录或注册成功后返回 `access_token`、`refresh_token`、`token_type` 和 `user`。受保护接口使用：

```text
Authorization: Bearer <access_token>
```

当前密码使用 PBKDF2-SHA256 哈希保存。短期 `access_token` 使用带过期时间的 HMAC 签名 token；`refresh_token` 只在服务端保存哈希并且每次刷新后轮换。客户端退出登录时调用 `/auth/logout` 撤销刷新令牌。

## 当前已实现：任务

```text
GET    /tasks
POST   /tasks
PATCH  /tasks/{task_id}
DELETE /tasks/{task_id}
```

创建任务：

```json
{
  "title": "完成接口设计",
  "description": "可选备注",
  "due_at": "2026-09-30T12:00:00Z",
  "reminder_offset_minutes": 30
}
```

`reminder_offset_minutes` 表示在截止时间前多少分钟提醒，允许 `0` 到 `10080`；传 `null` 表示不设置提醒。任务当前字段：`id`、`title`、`description`、`due_at`、`reminder_offset_minutes`、`completed`、`completed_at`、`created_at`、`updated_at`。未完成任务按截止时间排列，已完成任务按完成时间倒序排列，因此刚完成的任务紧跟在未完成任务之后。完成时间用于控制主页保留 3 天、日历保留 1 年；任务数据本身不会因此自动删除。所有任务查询和修改都按当前登录用户隔离。

Android 客户端会根据任务的截止时间和提醒偏移量设置本地 AlarmManager，并通过系统通知渠道提醒用户；已完成任务或已过期提醒不会重复调度。

## 当前已实现：截图 AI 候选

```text
POST /ai/parse-screenshot
```

请求使用 `multipart/form-data`，字段如下：

- `file`：JPG、PNG 或 WebP 图片，最大 10 MB。
- `reference_at`：可选，截图对应的参考时间，ISO-8601 格式。
- `timezone_name`：可选，默认 `Asia/Shanghai`。

服务端会先把原图保存到 MinIO，再调用 OpenAI 兼容的视觉模型，返回待确认候选任务。模型提示会要求把“明天、下周一、今晚”等相对时间按照 `reference_at` 和用户时区换算成绝对时间。客户端确认后才调用 `/tasks` 正式创建任务。

服务端默认使用 Google AI Studio 的 Gemini OpenAI 兼容接口和 `gemini-2.5-flash-lite`，只需在 `.env` 配置 `AI_API_KEY` 即可；也可以通过 `AI_API_BASE_URL` 和 `AI_MODEL` 切换到其他兼容平台。密钥只保存在服务器，不提交到 GitHub。

返回示例：

```json
{
  "attachment_id": 1,
  "parse_id": 1,
  "candidates": [
    {
      "title": "周五前提交报告",
      "description": null,
      "due_at": "2026-09-25T18:00:00+08:00",
      "reminder_offset_minutes": null,
      "confidence": 0.92,
      "source_text": "周五前把报告发我"
    }
  ]
}
```

## 规划中的接口

### 日历与提醒

```text
GET    /calendar?from=...&to=...
GET    /tasks/{task_id}/reminders
POST   /tasks/{task_id}/reminders
PATCH  /reminders/{reminder_id}
DELETE /reminders/{reminder_id}
```

提醒使用 `offset_minutes` 表示相对截止时间的提前量，也支持直接指定 `remind_at`。

### 截图和 AI 扩展

```text
POST /uploads/presign
GET  /ai/jobs/{job_id}
POST /ai/jobs/{job_id}/confirm
```

AI 结果先进入待确认状态，不直接覆盖已有任务。结果需要包含解析出的标题、时间、来源时间、时区和置信度。

## 统一错误格式

当前 FastAPI 错误响应使用 `detail` 字段，例如：

```json
{
  "detail": "任务不存在"
}
```

后续统一错误中间件会补充错误码、请求 ID 和结构化详情。
