# 协作分工与分支规范（Kiro / Codex）

本仓库由两个 AI 协作者配合开发：Kiro 和 Codex（GPT）。本文是双方都必须遵守的现行规范。
2026-10-10 的前端比拼已经结束，比拼期间按文件划分归属的旧规则不再适用，以本文为准。

## 1. 角色分工

### Kiro：主要维护者

- 负责功能、界面（Android 客户端）、服务端业务逻辑的修改。
- 负责代码合并：开 PR、通过 CI 后合并进 `main`，以及服务器部署。
- 发版时负责定版本号、开升版本号的 PR 并合并、起草发布说明；打标签和发布 Release 交给仓库所有者用 Codex 完成（见第 4 节）。
- 可以修改仓库中的任何文件。修改前先说明计划，较大改动或生产操作要等仓库所有者确认。
- 基础操作（构建、测试、ADB 安装、看日志）默认交给 Codex 执行，Kiro 只复核结果（见第 4 节）。

### Codex：基础支持

Codex 默认只做以下基础工作：

- 构建、测试和运行应用（Gradle 测试与构建、服务端 pytest），包括构建签名 Release APK 并核对版本号、证书和对齐
- 用 ADB 把应用安装到真机，启动并查看崩溃日志
- 查看日志、定位基础故障
- 检查 Git 状态和分支，复核 PR 的 CI 结果
- 提供操作方法，或复核 Kiro 的结果
- 在仓库所有者交互式操作时，按第 4 节流程打标签、发布 Release

除非仓库所有者明确要求，Codex 不主动做下面这些事：

- 修改功能、界面或服务端业务代码
- 合并或改写 Kiro 的提交和分支（不 merge、不 rebase、不 cherry-pick、不 reset）
- 推送 `main`、部署服务器
- 在被 Kiro 非交互驱动时打标签、发布 Release（这两步只在仓库所有者交互式操作时进行）

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

## 4. 基础任务交给 Codex

### 4.1 Kiro 驱动 Codex（构建、测试、安装、日志）

Kiro 在自己的会话里用 `codex exec` 非交互地调用 Codex，Codex 只在 `D:\L\Study\TodoList-codex` 里工作，结果写进文件，由 Kiro 复核后再汇报。

```powershell
# 1. 让 Codex 的 worktree 指向要测的提交
git -C D:\L\Study\TodoList-codex fetch origin
git -C D:\L\Study\TodoList-codex switch --detach origin/main   # 或指定提交

# 2. 只读任务（看 Git 状态、读日志、复核）
codex exec -C D:\L\Study\TodoList-codex -s read-only --ephemeral -o <结果文件> "<任务说明>"

# 3. 需要构建或 ADB 的任务
codex exec -C D:\L\Study\TodoList-codex -s workspace-write `
  --add-dir C:\Users\liujiabo\.gradle --add-dir C:\Users\liujiabo\.android `
  --ephemeral -o <结果文件> "<任务说明>"
```

规则：

1. 任务说明要写清楚：具体命令、"不要修改跟踪文件"、要回报哪些内容（BUILD 结果、测试数、`adb` 输出、出错时的原文）。
2. 只用 `read-only` 或 `workspace-write` 沙箱。不使用 `--dangerously-bypass-approvals-and-sandbox` 或任何关闭沙箱的参数。
3. 这种模式下 Codex 读不到 `gh` 的登录凭据（保存在 Windows 凭据管理器），所以不做任何需要 GitHub 写权限的事。
4. Kiro 必须复核 Codex 的结论：查看结果文件，必要时用 `git -C D:\L\Study\TodoList-codex status` 确认没有改动，再向仓库所有者汇报。
5. 一次完整的 Gradle 测试加构建大约需要 6 分钟，并消耗仓库所有者 ChatGPT 账号的额度。紧急排障时，Kiro 可以自己直接执行。
6. 非交互沙箱里构建的调试包不保证能覆盖安装手机上现有的应用：沙箱读不到 `C:\Users\liujiabo\.android\debug.keystore`（会另生成一张调试证书，例如 `ba5011e6...`，或因拿不到 `debug.keystore.lock` 而构建失败）。手机上的应用使用证书 `8af07bcd...938aec`。因此：
   - 真机验证：由 Kiro 在自己的工作目录构建 APK，再让 Codex 安装这个已构建好的文件（任务说明里写明"不要构建，只安装指定路径的 APK"）。
   - 正式发布：使用仓库所有者复制到 Codex 工作目录的 `mobile/keystore.properties` 构建签名 Release（见 4.2）。
   - 安装前必须用 `apksigner verify --print-certs` 确认证书 SHA-256 是 `8af07bcd5ca4f95d9877d0b54fdd472b96aff91dd465f4de9f51261637938aec`，不一致就停止，绝不为了装上而卸载或清除数据。

### 4.2 仓库所有者用 Codex 发布 Release

前提：Kiro 已经把升版本号的 PR 合并进 `main`，并把发布说明草稿交给仓库所有者（例如放在 `artifacts/release-notes-<版本>.md`，该目录不入库）。

仓库所有者在 `D:\L\Study\TodoList-codex` 里交互式启动 Codex（`codex`），把下面的指令交给它，执行 `git push`、`gh` 等命令时按提示批准：

```text
发布 TodoCloud v<版本>。不要修改任何源代码、版本号或已有标签。
1. git fetch origin --tags；git switch --detach origin/main；确认 mobile/app/build.gradle.kts 的 versionName 是 <版本>，且不存在标签 v<版本>，否则停止并报告。
2. 确认 mobile/keystore.properties 已存在（由仓库所有者复制，不要读取或打印其内容），运行 mobile\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleRelease，必须 BUILD SUCCESSFUL。
3. 用 build-tools 36.0.0 的 apksigner verify --print-certs、zipalign -c 4、aapt dump badging 核对：签名有效，证书 SHA-256 为 8af07bcd5ca4f95d9877d0b54fdd472b96aff91dd465f4de9f51261637938aec，包名 com.todocloud.app，versionName/versionCode 与 build.gradle.kts 一致。任何一项不符就停止。
4. 手机已连接时：adb -s a1b797ec install -r 安装并启动，确认没有崩溃，不清数据、不卸载。
5. git tag -a v<版本> -m "TodoCloud <版本>" origin/main，然后 git push origin tag v<版本>。
6. Copy-Item 'mobile/app/build/outputs/apk/release/app-release.apk' 'mobile/app/build/outputs/apk/release/TodoCloud-v<版本>.apk' -Force；确认复制后的 SHA-256 与原 APK 一致；再运行 gh release create v<版本> 'mobile/app/build/outputs/apk/release/TodoCloud-v<版本>.apk' --repo fastbit-ljb/todocloud --title "TodoCloud <版本>" --notes-file <发布说明文件> --verify-tag --latest
7. gh release view 核对：非草稿、非预发布，附件存在且大小不为 0，附件 SHA-256 与本地文件一致。用中文汇报结果。
```

发布后，仓库所有者把 Release 链接告诉 Kiro，由 Kiro 记录并确认版本状态。
