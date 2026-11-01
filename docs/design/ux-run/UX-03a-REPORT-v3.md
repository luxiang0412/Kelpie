# UX-03a（按原型重做）report — 对话顶栏与 Agent 标识对齐 `chat-*.png`

- baseline：`ae1032d` / `ux/graphite-editorial`
- 依据：原型截图 `herdr-ux-d0-chat-zh-light.png`（本次逐项对照）+ DESIGN-SYSTEM §4.2
- 之前的问题：顶栏用文字「看板」当返回、状态用彩色胶囊、Agent 标识只有裸文字，与原型不同。
- 改动（`ConversationScreen.kt` + 2 个矢量图标）：
  1. 返回改为**矢量箭头**（`ic_back`，48dp 触达）
  2. 标题下副标题统一为 **`Agent · 项目 · 状态`**（如 `Pi · spike-sandbox · 空闲`），状态用**文字**而非彩色胶囊；去掉顶栏的状态 Chip
  3. Agent 分组标识增加 **16dp 矢量图标**（`ic_agent`）+ 13sp 中性文字，仍是一组只出现一次
- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (28 tests)**
- 截图（**真机** `192.168.1.8:39527`，Dark，zh）：
  - After：`spike/results/real-proto-chat-light.png`（实际是深色，文件名沿用）
    - 可见：`←` 返回、`Pi · spike-sandbox` 标题、`Pi · spike-sandbox · 空闲` 副标题、搜索图标、`更多`
    - Agent 组首：`<>  Pi`；正文列表与行内码背景；消息尾 `复制 更多`；圆形 `↑` 发送；底部三态说明（`指令已接收` / `发送结果无法确认`）
  - Before：`spike/results/real-conv.png`（文字「看板」返回 + 绿色状态胶囊）
- 与原型仍有差异（如实列出）：
  1. 原型顶栏右侧是「搜索 + ⋯」；我们是「搜索 + 更多（文字）」——更多仍是文字按钮，未图标化
  2. 原型有 Tool 卡展开态；本截图会话里没有工具调用
  3. 原型用户气泡浅灰；真机为深色主题，气泡为深灰（主题差异，非结构差异）
- 回滚：`git revert <this commit>`
