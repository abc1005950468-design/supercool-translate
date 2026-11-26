# Agent 工作流

## 角色

- `codex-master`：唯一主控，接单、分派、最终闸门
- `claude-vscode`：独立验收、高难修复
- `claude-desktop`：只读任务卡与复现设计
- `vietnam-agy`：中等难度、UI、探索
- `vietnam-codex`：局部代码、测试、脚本
- `doubao-archive`：证据整理与归档

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
