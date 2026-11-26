# 共轨看板

GitHub Projects 需要额外 `project` Token 权限，当前使用中央任务仓 Issue 作为唯一执行状态源：
[gonggui-task-center](https://github.com/abc1005950468-design/gonggui-task-center)。
项目入口与验收入口见注册总览：[#26](https://github.com/abc1005950468-design/gonggui-task-center/issues/26)。

## 中央任务映射

| 中央任务 | 中央 Issue | 源码仓问题 | 当前状态 | 内容 |
| --- | --- | --- | --- | --- |
| `TASK-20261126-0011` | [#21](https://github.com/abc1005950468-design/gonggui-task-center/issues/21) | [#1](https://github.com/abc1005950468-design/supercool-translate/issues/1) | `status:draft` | NLLB 内存强退 |
| `TASK-20261126-0012` | [#22](https://github.com/abc1005950468-design/gonggui-task-center/issues/22) | [#2](https://github.com/abc1005950468-design/supercool-translate/issues/2) | `status:ready` | API Key 持久化 |
| `TASK-20261126-0013` | [#23](https://github.com/abc1005950468-design/gonggui-task-center/issues/23) | [#4](https://github.com/abc1005950468-design/supercool-translate/issues/4) | `status:ready` | 依赖恢复与构建 |
| `TASK-20261126-0014` | [#24](https://github.com/abc1005950468-design/gonggui-task-center/issues/24) | [#3](https://github.com/abc1005950468-design/supercool-translate/issues/3) | `status:ready` | 连续中文语音稳定性 |
| `TASK-20261126-0015` | [#25](https://github.com/abc1005950468-design/gonggui-task-center/issues/25) | [#5](https://github.com/abc1005950468-design/supercool-translate/issues/5) | `status:draft` | 可重复构建与侧载发布 |

## Ready

- #1 [P0] 修复 NLLB 600M 在 Nubia NX769J 被系统 SIGKILL
- #2 [P1] 验证 API Key 自动保存与切离线保留
- #3 [P1] 连续中文语音稳定性压力测试
- #4 [P1] 发布二进制依赖包并补全构建恢复
- #5 [P2] 建立可重复构建与侧载发布流水线

## 状态标签

- `status:ready`
- `status:claimed`
- `status:fixing`
- `status:review`
- `status:verified`
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
