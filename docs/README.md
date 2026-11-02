# 文档

| 文档 | 内容 |
| --- | --- |
| [development.md](development.md) | 开发指南：构建、测试、发版与签名、调试技巧、多语言、性能数据 |
| [design-spec.md](design-spec.md) | 设计规范：信息架构、视觉 token、各页面规格 |
| [herdr-api.md](herdr-api.md) | 客户端用到的 herdr 远程接口，以及还缺什么 |
| [approvals.md](approvals.md) | 手机审批（Claude Code / Codex）：机制、实验结果、启用前要决定的事 |
| [push-options.md](push-options.md) | 后台推送方案对比（FCM / ntfy / UnifiedPush）；目前不做，靠常驻连接 |
| [markdown-rendering.md](markdown-rendering.md) | Markdown 渲染选型 |
| [background.md](background.md) | 代码注释里引用的旧决策（`Dn`）和旧问题（`I-n`）摘录 |
| [../companion/README.md](../companion/README.md) | 开发机脚本：扫码配对、审批 hook |
| [../CHANGELOG.md](../CHANGELOG.md) | 版本更新说明 |

## history/

开发过程记录，只追加、不修改：

- [history/run-log.md](history/run-log.md)：每次远端写操作、每个阶段的自查、早报。
- [history/remote-scope.md](history/remote-scope.md)：开发期间对远端主机的授权范围。
- [history/redesign-brief.md](history/redesign-brief.md)：重写前的调研简报。

## 历史

- 0.1.x 是旧版客户端（原 `android/`），2026-10 底。
- 2026-11-02 起重写为 Signal（0.2.0）。
- 2026-11-03：旧客户端代码、早期探针、旧版文档（设计规格、过程报告、截图证据，以及 `DECISIONS.md`、`ISSUES.md`、`RESULTS.md` 等台账）都已从仓库删除。
- 同一天，Android 工程从 `signal/` 挪到仓库根目录，文档从 `docs/signal/` 挪到 `docs/`。
- 删掉的内容可以从 git 历史取，例如 `git show signal-v0.2.0:DECISIONS.md`、`git checkout signal-v0.3.0 -- archive/`。
