# UX-D0 交付包 · 放入仓库前必读

这是完整的 Herdr Android Graphite × Editorial 设计与实施资料包。

## 1. 如何导入

1. 先确认 Git 工作树干净；如存在未提交更改，不要直接解压覆盖相同文件。
2. 将 ZIP **解压到 Herdr Android 仓库根目录**。ZIP 内自带 `docs/design/...` 目录，包含设计规范、详细总任务书及视觉原型。
3. 确认：`docs/design/tasks/UX-D0-AUTONOMOUS-MASTER-PLAN.md` 存在。
4. 打开根目录 `PI-START-HERE.md`，把内容作为唯一启动 Prompt 发给本地 Pi。
5. Pi 应创建独立开发分支，按编号执行任务，不自动合并 main。

## 2. 文件定位

| 文件/目录 | 作用 |
|---|---|
| `PI-START-HERE.md` | 复制给 Pi 的单条启动指令 |
| `docs/design/tasks/UX-D0-AUTONOMOUS-MASTER-PLAN.md` | **完整自主运行规则 + 24 个细分任务** |
| `docs/design/tasks/UX-D0-TASK-TRACKER.md` | 任务进度表模板 |
| `archive/docs/HERDR-UX-01a-PI-TASK-PACKET-DRAFT.md` | UX-01a 的更细验收规格 |
| `docs/design/ux-d0/HERDR-UX-D0-DESIGN-SYSTEM-v0.1.md` | 共用 Design Tokens、状态、i18n、组件 |
| `docs/design/ux-d0/HERDR-UX-D0-BOARD-SPEC-v0.2.md` | 工作台 |
| `docs/design/ux-d0/HERDR-UX-D0-CONVERSATION-SPEC-v0.2.md` | 对话与工具记录 |
| `docs/design/ux-d0/HERDR-UX-D0-COMPOSER-SPEC-v0.2.md` | 输入、语音、草稿与发送状态 |
| `docs/design/ux-d0/prototype` | 纯 HTML 视觉原型及截图，仅为设计参考 |

## 3. 决策边界

用户本轮授权 Pi **连续实施本地 Android App 的可回滚 UI/UX 工作**，但原 UX-D0 的两个待批准布局仅作为暂定实施默认值，用户醒后仍需检查真机效果。所有 SSH/Herdr 远端变更、生产数据破坏、危险操作和自动合并 main 均不在范围内。

## 4. 睡醒后查看什么

阅读 `docs/design/ux-run/PROGRESS.md` 与 `FINAL-REVIEW.md`，查看 Android 真机/AVD 截图、工作分支及 Commit；未执行的任务必须保留真实阻断原因。**不要只看“已实现”字样，必须看实测/截图/测试证据。**
