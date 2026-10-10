# 协作分工与分支规范（Kiro / Codex）

本仓库由两个 AI 协作者配合开发：Kiro 和 Codex（GPT）。本文是双方都必须遵守的现行规范。
2026-10-10 的前端比拼已经结束，比拼期间按文件划分归属的旧规则不再适用，以本文为准。

## 1. 角色分工

### Kiro：主要维护者

- 负责功能、界面（Android 客户端）、服务端业务逻辑的修改。
- 负责代码合并：开 PR、通过 CI 后合并进 `main`，以及发版和服务器部署。
- 可以修改仓库中的任何文件。修改前先说明计划，较大改动或生产操作要等仓库所有者确认。

### Codex：基础支持

Codex 默认只做以下基础工作：

- 构建、测试和运行应用（Gradle 测试与构建、服务端 pytest）
- 用 ADB 把应用安装到真机
- 查看日志、定位基础故障
- 检查 Git 状态和分支
- 提供操作方法，或复核 Kiro 的结果

除非仓库所有者明确要求，Codex 不主动做下面这些事：

- 修改功能、界面或服务端业务代码
- 合并或改写 Kiro 的提交和分支（不 merge、不 rebase、不 cherry-pick、不 reset）
- 推送 `main`、打标签、发布 Release、部署服务器

发现需要改代码的问题时，Codex 把现象、日志和定位结论交给 Kiro，由 Kiro 来改。

## 2. 分支与工作目录隔离

| 对象 | Kiro | Codex |
|------|------|-------|
| 工作目录 | `D:\L\Study\TodoList` | 自己的 worktree，例如 `D:\L\Study\TodoList-codex` |
| 分支 | `kiro/<主题>` 功能分支 | `codex/<主题>`，只在被明确要求改代码时使用 |
| `main` | 只通过 PR 合并 | 只读：可以拉取、查看、构建，不能写 |

规则：

1. 双方不在同一个工作目录里同时工作。同一目录同时切分支、同时跑 Gradle，会把改动落到别人的分支上，Gradle 也会因为抢锁卡死（2026-10-10 发生过）。
2. Codex 需要构建或运行时，在自己的 worktree 里检出要测的提交：
   ```powershell
   git -C D:\L\Study\TodoList fetch origin
   git -C D:\L\Study\TodoList worktree add D:\L\Study\TodoList-codex origin/main --detach
   ```
   以后再测，在该目录里执行 `git fetch origin` 和 `git switch --detach origin/main`（或指定的提交）。第一次构建前把 `mobile/local.properties` 复制过去；`mobile/keystore.properties` 只在需要构建签名 Release 时才复制。
3. 每次修改、构建或提交前，先用 `git branch --show-current` 和 `git status` 确认目录和分支，不对的话停下来。
4. 双方都不修改对方的分支。Codex 只读 `kiro/*` 分支；如果被要求改代码，就在 `codex/<主题>` 上做，提 PR，由 Kiro 审查和合并。
5. `main` 只通过 PR 更新。Kiro 的本地策略禁止直接推送 `main`，需要直接推送时由仓库所有者亲自执行。

## 3. 冲突与协调

- 跨角色的改动由需求方提出，由负责的一方实施。Codex 发现问题交给 Kiro 修改；Kiro 需要复核时请 Codex 构建、安装或检查日志。
- 合并冲突由 Kiro 处理。冲突涉及 Codex 的 `codex/*` 分支时，保留双方意图，拿不准就交仓库所有者裁决，不擅自丢弃任何一方的改动。
- 真机验证时不点会删除或改写线上数据的操作（例如真正的"删除"按钮），只验证取消路径；手机锁屏时请仓库所有者解锁，不尝试绕过。
- 签名密钥、服务器私钥、`.env` 都由仓库所有者保管，双方都不读取、不打印、不提交。
