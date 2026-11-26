# Agent 工作流

## 角色

- `codex-master`：唯一主控，接单、分派、最终闸门
- `claude-vscode`：独立验收、高难修复
- `claude-desktop`：只读任务卡与复现设计
- `vietnam-agy`：中等难度、UI、探索
- `vietnam-codex`：局部代码、测试、脚本
- `doubao-archive`：证据整理与归档

## 中央任务中心

超酷翻译执行状态统一在
[gonggui-task-center](https://github.com/abc1005950468-design/gonggui-task-center)
维护，项目标签为 `project:supercool-translate`。

源码仓 Issue 只记录产品问题；执行 Agent 必须先在中央 Issue 完成领取：

```powershell
python scripts/taskctl.py remote-list --label project:supercool-translate
python scripts/taskctl.py remote-list --label status:ready
python scripts/taskctl.py status --issue <number>
python scripts/taskctl.py claim --issue <number> --agent <agent-id>
```

标准状态链路：

```text
status:ready
→ /claim <agent-id>
→ status:claimed
→ status:fixing
→ /progress
→ /submit <agent-id> <pr-url>
→ status:review
→ claude-vscode verdict
→ status:verified
→ codex-master 最终闸门
```

当前映射为 `TASK-20261126-0011`～`0015` 对应中央 Issue `#21`～`#25`；完整表格见 `PROJECT_BOARD.md`。源码仓 Issue 可以作为证据链接，但不能作为领取或验收状态源。

## 标准任务卡

必须包含：

1. 目标与用户可见结果
2. 当前证据
3. 允许修改路径
4. 禁止修改路径
5. 验收命令
6. 回滚方式

## 完成标准

- 代码完成不等于任务完成
- 必须附测试、构建、真机/浏览器证据
- 审查者输出 `completed` / `not_completed`
- 生产或公开发布由主控执行
