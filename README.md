# Herdr Signal

人离开电脑后，用手机监督远程开发机上由 [herdr](https://herdr.dev) 托管的 coding agent（Claude Code / Codex / Pi）：
谁在等你、在等什么，读结构化对话，补发指令，批准工具调用。**不是**移动 SSH 终端。

![logo](signal/art/logo-512.png)

## 目录

| 目录 | 内容 |
| --- | --- |
| [`signal/`](signal/) | **当前的 Android 客户端**（Kotlin / Compose）。构建、运行、发版见 [`signal/README.md`](signal/README.md) |
| [`signal/companion/`](signal/companion/) | 装在开发机上的小脚本：扫码配对（`pair/`）、手机审批 hook（`hooks/`） |
| [`docs/signal/`](docs/signal/) | 设计规范、herdr 接口审计、审批实验、过程记录（索引见下） |
| [`archive/`](archive/) | 不再演进的旧代码：旧版客户端 `archive/android/`、早期探针 `archive/spike/` |
| `tools/` | 仓库维护脚本（`check_doc_links.py`：检查文档里的路径引用） |
| `.github/workflows/signal.yml` | CI：单元测试、截图回归、lint、构建、文案检查 |

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [`signal/README.md`](signal/README.md) | 模块结构、构建命令、发版与签名、扫码配对、调试技巧、性能数据 |
| [`signal/CHANGELOG.md`](signal/CHANGELOG.md) | 版本更新说明 |
| [`docs/signal/DESIGN-SPEC.md`](docs/signal/DESIGN-SPEC.md) | 设计规范（信息架构、视觉 token、各页面规格） |
| [`docs/signal/HERDR-AUDIT.md`](docs/signal/HERDR-AUDIT.md) | 客户端用到的 herdr 远程接口，以及还缺什么 |
| [`docs/signal/APPROVALS.md`](docs/signal/APPROVALS.md) | 手机审批（Claude Code / Codex）的机制、实验结果、启用前要决定的事 |
| [`docs/signal/PUSH-OPTIONS.md`](docs/signal/PUSH-OPTIONS.md) | 后台推送方案对比（FCM / ntfy / UnifiedPush）；目前不做，靠常驻连接 |
| [`docs/signal/MARKDOWN-RENDERING.md`](docs/signal/MARKDOWN-RENDERING.md) | Markdown 渲染选型 |
| [`docs/signal/BACKGROUND.md`](docs/signal/BACKGROUND.md) | 代码注释里引用的旧决策（`Dn`）、旧问题（`I-n`）摘录 |
| [`docs/signal/REMOTE-SCOPE.md`](docs/signal/REMOTE-SCOPE.md) | 开发过程中对远端主机的授权范围 |
| [`docs/signal/RUN-LOG.md`](docs/signal/RUN-LOG.md) | 开发过程记录：每次远端写操作、每个阶段的自查 |
| [`docs/signal/REDESIGN-BRIEF.md`](docs/signal/REDESIGN-BRIEF.md) | 重写前的调研简报（历史） |

## 历史

- 0.1.x：旧版客户端（现 `archive/android/`），2026-10 底。
- 2026-11-02：在 `signal/` 重写（新设计、新架构），0.2.0 起是 Signal。
- 2026-11-03：旧版的设计规格、过程报告、截图证据、台账（`DECISIONS.md`、`ISSUES.md`、`RESULTS.md` 等）从仓库删除，需要时从 git 历史取，例如 `git show signal-v0.2.0:DECISIONS.md`。
