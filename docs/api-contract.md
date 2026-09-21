# API 约定

API 前缀为 `/api/v1`，请求和响应使用 JSON，除文件上传接口外均使用 UTF-8。

## 当前已实现：认证

```text
POST /auth/register
POST /auth/login
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

登录或注册成功后返回 `access_token`、`token_type` 和 `user`。受保护接口使用：

```text
Authorization: Bearer <access_token>
```

当前密码使用 PBKDF2-SHA256 哈希保存，令牌为带过期时间的 HMAC 签名 token。正式版本仍需补充 refresh token、撤销和设备会话管理。

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
  "due_at": "2026-09-30T12:00:00Z"
}
```

任务当前字段：`id`、`title`、`description`、`due_at`、`completed`、`created_at`、`updated_at`。所有任务查询和修改都按当前登录用户隔离。

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

### 截图和 AI

```text
POST /uploads/presign
POST /ai/parse-screenshot
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
