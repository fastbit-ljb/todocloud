# API 约定

API 前缀为 `/api/v1`，请求和响应使用 JSON，除文件上传接口外均使用 UTF-8。

## 认证

```text
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /me
```

登录成功后返回短期 `access_token` 和长期 `refresh_token`。客户端使用 Bearer Token 调用受保护接口。

## 任务

```text
GET    /tasks
POST   /tasks
GET    /tasks/{task_id}
PATCH  /tasks/{task_id}
DELETE /tasks/{task_id}
```

任务至少包含：标题、描述、状态、优先级、截止时间、用户时区、来源和更新时间。

## 日历与提醒

```text
GET    /calendar?from=...&to=...
GET    /tasks/{task_id}/reminders
POST   /tasks/{task_id}/reminders
PATCH  /reminders/{reminder_id}
DELETE /reminders/{reminder_id}
```

提醒使用 `offset_minutes` 表示相对截止时间的提前量，也支持直接指定 `remind_at`。例如提前 30 分钟就是 `offset_minutes = 30`。

## 截图和 AI

```text
POST /uploads/presign
POST /ai/parse-screenshot
GET  /ai/jobs/{job_id}
POST /ai/jobs/{job_id}/confirm
```

AI 结果先进入待确认状态，不直接覆盖已有任务。结果需要包含解析出的标题、时间、来源时间、时区和置信度，遇到歧义时交给用户确认。

## 统一错误格式

```json
{
  "code": "TASK_NOT_FOUND",
  "message": "任务不存在",
  "details": null,
  "request_id": "req_..."
}
```

