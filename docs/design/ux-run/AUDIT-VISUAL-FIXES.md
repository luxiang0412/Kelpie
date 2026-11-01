# 视觉审计修复 — #3 / #4 / #6（真机截图确认）

- baseline：`299cbdc` / `ux/graphite-editorial`
- 设备：真机 `192.168.1.8:39527`，zh，Light，393dp

| # | 文档依据 | 改动 | 截图 |
| --- | --- | --- | --- |
| 3 | CONVERSATION-SPEC §5.1「气泡圆角 16dp，靠尾部一角可用 6dp」 | `RoundedCornerShape(topStart=16, topEnd=16, bottomStart=16, **bottomEnd=6**)` | `audit-conv4.png` |
| 4 | 原型 `board-zh-light.png`：分区标题 `已完成 · 1` | 分区标题在标题与计数之间加中点分隔（`已完成 · 1`、`空闲 · 1`） | `audit-board2.png` |
| 6 | DESIGN-SYSTEM §2.4「装饰图标不重复朗读」 | Agent 组首（`<>` 图标 + `Pi`）外层加 `semantics(mergeDescendants = true)`，TalkBack 只读一次 | `audit-conv4.png` |

- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (28 tests)**
- 仍未处理（**记录在案，不掩藏**）：
  - #8 行内码 4–6dp 圆角背景：Compose `SpanStyle` 不支持圆角，需要 `InlineContent` 或自绘；当前保留矩形背景（字符保真优先）
  - #5 工作台标题行的「搜索 / 主题」图标：我们保留既有「刷新」，不新增未实现入口
  - #2 Mic / 附件：能力缺口（UX-07b SKIPPED）
