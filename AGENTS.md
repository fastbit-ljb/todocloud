# Agent instructions

This repository is maintained by two AI agents. Before doing anything, read
[docs/branch-collaboration.md](docs/branch-collaboration.md) (in Chinese) — it is the binding
division of work and branch/worktree isolation policy.

Summary for Codex / GPT agents:

- Kiro is the primary maintainer: features, UI, server business logic, merging, releases, deploys.
- By default you only build, test and run the app, install it on the device with ADB, read logs,
  diagnose basic failures, inspect Git state, and explain procedures or review results.
- Unless the repository owner explicitly asks, do NOT change features, UI or server business code,
  and do NOT merge, rebase or rewrite Kiro's branches. Never push `main`, tag, release or deploy.
- Work in your own worktree (e.g. `D:\L\Study\TodoList-codex`), never in `D:\L\Study\TodoList`,
  and check `git branch --show-current` before any build or edit.
- Never read, print or commit keystores, SSH keys or `.env` files.
