# UX-D0 执行进度

状态：`TODO / IN_PROGRESS / PASS / PARTIAL / BLOCKED / SKIPPED`

| Task | 状态 | 说明 |
| --- | --- | --- |
| UX-00 仓库审计与基线 | PASS | 分支 `ux/graphite-editorial`；审计与基线文档已提交 |
| UX-01a 对话顶栏大字体裁剪 | PASS | 自适应高度单行标题；fontScale 1.3 真机验证；UI 16/16 无回归 |
| UX-01b InlineCode 保真 | PASS | 移除空格伪 padding；新增 2 项 UI 保真断言（AVD 通过） |
| UX-00a 基础 Tokens/Theme/图标契约 | PASS | 色值对比度 34 项 + 4 个矢量图标 + Component/字阶契约；页面留白组件待接入（转 UX-02a） |
| FIX-01 未捕获 SSH 异常致崩溃 | PARTIAL | handler + runCatching 已加；设备侧复现失败（WiFi adb 被杀），未验证 |
| UX-02a 工作台顶栏/主机选择器/底部导航 | PASS | 主机选择器下移、窄屏换行、分隔线；修好真机后已补 P1/P2 截图；UI 18/18 |
| UX-02b Attention First 列表 | PASS | done 默认收起（BOARD-DONE-001）、blocked 前2条卡片、idle/unknown 分区、行内单一语义色；UI 18/18 |
| 其余 Task Packets | TODO | 待前序完成后按序执行 |
| UX-03a 对话正文/身份头/用户气泡 | PARTIAL | 同上；UI 回归未重跑 |

> 本轮结束状态见 `FINAL-REVIEW.md`：`FUNCTIONAL_PARTIAL`。`UX-03b…UX-09` 未实施（会话预算，非设备阻断）。
| UX-09 全局 QA / 性能回归 | PARTIAL | 见 UX-09-QA.md |
