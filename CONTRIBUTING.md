# 协作与提交

## 开始任务

1. 从最新 `main` 创建 `agent/<角色>/<任务>` 分支。
2. 复制 `tasks/TASK_TEMPLATE.md` 到 `tasks/active/`，填写范围和验收命令。
3. 只修改任务明确允许的路径。

## 提交前

```powershell
gradle -p android testDebugUnitTest
gradle -p android assembleDebug
```

UI、语音、OCR、模型加载和内存改动还需要真机验证，并把结果写入 PR。

## Pull Request

- 说明用户可见变化和风险。
- 列出验证命令与结果。
- 标注未验证项，不用“应该可以”代替证据。
- 至少一位非作者完成审查。

## 大文件

- `android/app/src/main/assets/models/` 不提交。
- APK、ZIP、构建目录、`.cxx`、日志和测试截图不提交。
- 运行 `scripts/fetch-bundled-models.ps1` 恢复内置语音模型。
