# Agent instructions

This repository is maintained by two AI agents. Before doing anything, read
[docs/branch-collaboration.md](docs/branch-collaboration.md) (in Chinese) — it is the binding
division of work and branch/worktree isolation policy.

Summary for Codex / GPT agents:

- Kiro is the primary maintainer: features, UI, server business logic, merging, version bumps,
  deploys.
- By default you only build, test and run the app (including building and verifying the signed
  release APK), install it on the device with ADB, read logs, diagnose basic failures, inspect
  Git state and CI, and explain procedures or review results.
- When Kiro runs you non-interactively (`codex exec`), stay in the read-only or workspace-write
  sandbox, do not edit tracked files unless told to, and report exact command output.
- Debug APKs you build in that sandbox are signed with a different debug key and will not
  install over the app on the device. Install only APKs whose certificate SHA-256 is
  `8af07bcd5ca4f95d9877d0b54fdd472b96aff91dd465f4de9f51261637938aec` (check with
  `apksigner verify --print-certs`); never uninstall or clear data to make an install succeed.
- Tagging and publishing a GitHub Release happen only when the repository owner runs you
  interactively, following section 4.2 of the policy. Never do them when driven by Kiro.
- Unless the repository owner explicitly asks, do NOT change features, UI or server business code,
  and do NOT merge, rebase or rewrite Kiro's branches. Never push `main` or deploy.
- Work in your own worktree (`D:\L\Study\TodoList-codex`), never in `D:\L\Study\TodoList`,
  and check `git branch --show-current` before any build or edit.
- Never read, print or commit keystores, SSH keys or `.env` files.
