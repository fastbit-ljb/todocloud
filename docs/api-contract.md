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

重复任务的 `recurrence` 为 `daily`、`weekdays`、`weekly`、`monthly` 或 `null`。完成后，原记录保持 `completed=true` 和原来的日期，规则转移到新建的下一轮；原记录的只读字段 `completed_recurrence` 保留已完成那轮的规则，供客户端按当天、本周（周一开始）、本月判断吉祥物状态。旧记录无法可靠还原的规则保持 `null`。进入新周期只改变当前状态判断，不取消或删除日历里的往期完成记录。

## 当前已实现：收件匣想法

```text
GET    /ideas
POST   /ideas
PATCH  /ideas/{idea_id}
DELETE /ideas/{idea_id}
```

想法只要求 `content`；`duration_minutes`、`start_at`、`due_at` 和 `items` 都是可选项。想法不提供完成状态，也不会直接触发提醒。

## 当前已实现：AI 计划

```text
GET  /plans
POST /plans/generate
POST /plans/{plan_id}/messages
POST /plans/{plan_id}/accept
```

AI 结合未转化的收件匣想法和未完成任务生成草案。用户可以通过消息反复要求调整；采用前不会写入正式任务。默认保护早餐 `07:30-08:30`、午餐 `12:00-13:00`、晚餐 `18:00-19:00` 和睡眠 `23:00-07:00`，只有吃饭、旅行交通、就医或真正紧急的固定事项可以例外。

生成请求可传 `idea_ids` 指定本次允许 AI 参考的收件匣想法；传空数组表示不参考收件匣。即使没有收件匣想法或既有任务，用户也可以直接在 `message` 中描述计划，采用后这些直接描述生成的项目会创建为正式任务。

## 当前已实现：截图 AI 候选

```text
POST /ai/parse-screenshot
```

请求使用 `multipart/form-data`，字段如下：

- `file`：JPG、PNG 或 WebP 图片，最大 10 MB。
- `reference_at`：可选，截图对应的参考时间，ISO-8601 格式。
- `timezone_name`：可选，默认 `Asia/Shanghai`。

服务端会先把原图保存到 MinIO，再调用 OpenAI 兼容的视觉模型，返回待确认候选任务。模型提示会要求把“明天、下周一、今晚”等相对时间按照 `reference_at` 和用户时区换算成绝对时间。客户端确认后才调用 `/tasks` 正式创建任务。

服务端默认使用智谱 OpenAI 兼容接口 `https://open.bigmodel.cn/api/paas/v4/` 和免费视觉模型 `glm-4.6v-flash`；文字抽取、截图识别、普通计划和流式计划共用这套配置。通过服务器的安全配置提供智谱 `AI_API_KEY`，密钥不得进入客户端、Git 或日志。默认 `AI_THINKING_MODE=disabled` 关闭思考，不设置备用付费模型。

现有部署的环境变量优先于代码默认值。由 Gemini 切换时必须同时更新 `AI_API_BASE_URL`、`AI_MODEL` 和平台对应的 `AI_API_KEY`，并清空旧的 `AI_FALLBACK_MODEL`、`AI_REASONING_EFFORT`；不能把 Gemini 密钥用于智谱地址。只有模型的非敏感配置可用于部署核验，不输出密钥。`AI_THINKING_MODE` 可为 `disabled`、`enabled` 或空字符串（不发送此字段）；此参数只发往智谱官方域名，Gemini 的 `AI_REASONING_EFFORT` 只发往 Google 官方域名。其他兼容平台仍可通过 `AI_API_BASE_URL` 和 `AI_MODEL` 显式覆盖。

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

## 当前已实现：文字/语音转文字 AI 候选

```text
POST /ai/parse-text
```

请求使用 JSON：

```json
{
  "text": "明天6点把 Word 发给我",
  "reference_at": "2026-09-23T10:00:00Z",
  "timezone_name": "Asia/Shanghai"
}
```

`text` 最大 20,000 个字符。Android 端使用系统 `SpeechRecognizer` 将语音转换为可编辑文字，然后调用此接口；录音不会上传服务器，也不需要额外的付费语音 API。接口返回格式与截图解析相同，但 `attachment_id` 和 `parse_id` 为 `null`。客户端仍会先展示候选结果，确认后才创建任务，并按标题和截止时间跳过重复任务。

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
