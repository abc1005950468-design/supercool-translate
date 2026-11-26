# 超酷翻译共轨规则

## 产品边界

这是独立的中俄 Android 翻译项目。当前重点是侧载 App、离线能力、可重复构建和真机稳定性。

## 必读上下文

- 接手任务前读 `docs/PROJECT_STATE.md`、`docs/KNOWN_ISSUES.md` 和任务的 `tasks/TASK_TEMPLATE.md` 实例。
- 修改架构、模型或引擎前读 `docs/ARCHITECTURE.md`。
- 涉及发布时读 `docs/AGENT_WORKFLOW.md` 的验收与发布章节。

## 写者规则

- 一个任务、一个分支、一个写者；其他 Agent 做只读审查或评论。
- `main` 只通过 Pull Request 合并。
- 写者必须在 PR 中列出：目标、允许路径、禁止路径、测试命令、回滚方式。
- 独立审查者不得批准自己实现的代码。

## 分支与任务

- 分支名：`agent/<角色>/<短任务名>`。
- 执行状态统一在 [gonggui-task-center](https://github.com/abc1005950468-design/gonggui-task-center) 的中央 Issue 中维护，项目标签为 `project:supercool-translate`。
- 领取前先读项目注册总览：[#26](https://github.com/abc1005950468-design/gonggui-task-center/issues/26)。
- 源码仓 Issue 只记录产品问题；工作开始前必须先在中央 Issue 评论 `/claim <agent-id>`。
- 中央任务号 `TASK-20261126-0011` ～ `0015` 对应 Issue `#21` ～ `#25`；映射以 `PROJECT_BOARD.md` 为准。
- 执行中通过 `/progress` 写回，完成实现后用 `/submit <agent-id> <pr-url>` 进入 `status:review`。
- `claude-vscode` 独立验收后，`codex-master` 做最终闸门；修复者不得自审。
- 任务完成后更新 `docs/PROJECT_STATE.md` 的相关状态，但不要在多个文档重复同一事实。

## 验证门槛

- Kotlin 单测：`gradle -p android testDebugUnitTest`
- 构建：`gradle -p android assembleDebug`
- 真机验证必须记录设备型号、Android 版本、操作步骤、PSS/日志和最终结果。
- 内存相关改动至少覆盖：启动、首次翻译、连续语音、停止后 20 秒释放、进程是否存活。

## 安全

- 不提交 API Key、Cookie、Token、签名文件、`local.properties`、模型下载缓存或用户数据。
- 云端配置只保存到本机加密存储，仓库文档使用占位符。
- 大模型和构建产物通过脚本恢复，不进入 Git 历史。

## 当前关键风险

- NLLB 600M 在部分厂商机型可能触及单进程内存限制并被 `SIGKILL`。
- 在此问题解决前，不得宣称本地实时翻译已在全部设备稳定。
