# 协作规则：离线模型优先（2026-10-19）

## 默认路由

1. 任何任务先判断能否交给本机 Qwen3.8（Strata，`127.0.0.1:8080`）。
2. 能交 → 写任务卡 → 注入材料 → Qwen 产出 → Codex 验收。
3. 不能交，或连续两次失败 → Codex 接手，并在任务卡里记录原因。

**目标：Qwen 承担 70–80% 的 token；Codex 只承载决策与验收。**

## 任务类型与默认执行者

| 任务 | 默认 | 备注 |
| --- | --- | --- |
| 单文件/单模块代码生成 | Qwen | 接口与依赖先由 Codex 定 |
| 机械重构、批量替换 | Qwen | 输出 diff 或完整文件 JSON |
| 代码评审（P0/P1） | Qwen | Codex 复核并决定采纳 |
| 单元测试、测试数据 | Qwen | Codex 跑测试 |
| 俄语/中文 i18n | Qwen | 术语表固定译法 |
| 日志/大文件摘要 | Qwen | 数字用 `rg -c` 复核 |
| 模型目录、JSON 配置 | Qwen | 机器可判 |
| 架构、接口、模块边界 | Codex | 不外包 |
| 构建、编译错误根因 | Codex | Qwen 没有手 |
| 安全、隐私、许可 | Codex | DSH 可复核 |
| 发布、签名、生产 | Codex | 业主最终批准 |

## 任务卡规则

- 一卡一事；注入用 `@@FILE@@ / @@GREP@@ / @@HEAD@@ / @@LIST@@`。
- 输出必须是 JSON、diff 或完整文件，禁止“解释一下”。
- 抽取/评审用 `--effort none`；写代码用 `--effort low`。
- `max_tokens` 至少 4096；出现 `finish=length` 且正文为空，先降 effort 重试一次。
- 连续两次失败或需要新增依赖，Codex 接手。

## 验收规则

1. Qwen 的产出先落到 `work/answers/`，不直接改仓库。
2. Codex 检查信封、diff 范围、行号与引用。
3. 编译/单测必须由 Codex 跑；Qwen 的自述不作为通过依据。
4. 采纳的结论写入 `docs/decisions/`；原始产出保留在 `work/answers/`。

## 当前实例（M1 评审）

- 第一次 `effort low`：4096 token 全花在思考上，正文为空（`finish=length`）。
- 第二次 `effort none`：12 条发现，3 条真实高风险：
  1. 翻译协程与 `translator.close()` 竞争；
  2. 下载 `renameTo` 失败残留 `.part`；
  3. 云端请求未设长度。
- Codex 修复后 `assembleDebug` 通过。
