# Herdr Android — UX-D0 自主实施进度

> 此文件是初始模板。Pi 开始执行后，将其维护为 `docs/design/ux-run/PROGRESS.md` 的实时内容。状态值只能是 TODO / IN_PROGRESS / PASS / PARTIAL / BLOCKED / SKIPPED。不要预先标绿。

| ID | Task | 状态 | Commit | 关键证据 / 阻断 |
|---|---|---|---|---|
| UX-00 | 仓库审计与基线 | TODO | — | — |
| UX-01a | 对话大字体顶栏裁剪 | TODO | — | — |
| UX-01b | InlineCode 原文与复制 | TODO | — | — |
| UX-00a | Design Tokens / Theme | TODO | — | — |
| UX-02a | 工作台顶栏与主机选择器 | TODO | — | — |
| UX-02b | Attention First 与 Done 默认收起 | TODO | — | — |
| UX-02c | 工作台异常、缓存、Unknown | TODO | — | — |
| UX-03a | 对话正文与角色排版 | TODO | — | — |
| UX-03b | Markdown / Code 视觉 | TODO | — | — |
| UX-03c | Tool / Thinking / 长输出 | TODO | — | — |
| UX-03d | 复制、更多、文字选择 | TODO | — | — |
| UX-03e | 滚动锚点与新消息 | TODO | — | — |
| UX-04a | Composer 外观 | TODO | — | — |
| UX-04b | 长 Prompt / 草稿 / 写入状态 | TODO | — | — |
| UX-04c | 引用到 Composer | TODO | — | — |
| UX-05a | i18n 资源 | TODO | — | — |
| UX-05b | 语言设置/动态切换 | TODO | — | — |
| UX-06 | 当前会话搜索 | TODO | — | — |
| UX-07a | 语音转文字 Spike | TODO | — | — |
| UX-07b | 语音转文字实现（有条件） | TODO | — | — |
| UX-08a | 主机/设置/技术页样式 | TODO | — | — |
| UX-08b | 空态/异常态/Motion | TODO | — | — |
| UX-08c | 图标候选与启动页 | TODO | — | — |
| UX-09 | 集成测试与最终报告 | TODO | — | — |

## 当前必须保留的用户决策

- 工作台已完成分区默认收起。
- Agent 消息末尾常显“复制 + 更多”；长按正文使用原生文本选择。
- 语音功能实际可用后，正常宽度显示麦克风，窄屏或大字体时收到“更多”；语音不自动发送。
- 工作台标题下方主机选择器与轻量双层 Composer 是**本轮暂定实施默认值，待用户最终视觉验收**。

## 阻断记录模板

```text
Task ID:
阻断类别：代码/设备/依赖/安全/缺乏实际能力
客观证据：日志和命令（脱敏）
已经尝试：
已知能做的安全部分：
未实施的部分：
是否可继续独立任务：
```
