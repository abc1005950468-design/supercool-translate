# 共轨看板

GitHub Projects 需要额外 `project` Token 权限，当前先用 Issue 作为唯一任务源。

## Ready

- #1 [P0] 修复 NLLB 600M 在 Nubia NX769J 被系统 SIGKILL
- #2 [P1] 验证 API Key 自动保存与切离线保留
- #3 [P1] 连续中文语音稳定性压力测试
- #4 [P1] 发布二进制依赖包并补全构建恢复
- #5 [P2] 建立可重复构建与侧载发布流水线

## 状态标签

- `status:ready`
- `status:in-progress`
- `status:review`
- `status:blocked`

## Agent 标签

- `agent:codex-master`
- `agent:claude-vscode`
- `agent:claude-desktop`
- `agent:agency`
- `agent:vietnam-agy`
- `agent:vietnam-codex`
- `agent:doubao-archive`

## 写入规则

所有实现通过 `agent/<角色>/<任务>` 分支和 Pull Request 进入 `main`。每个 Issue 只指定一个写者。
