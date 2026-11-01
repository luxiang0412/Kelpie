# UX-04b（长编辑页按原型重做）report — 对齐 `focus-zh-light.png`

- baseline：`0bd8a27` / `ux/graphite-editorial`
- 依据：原型截图 `herdr-ux-d0-focus-zh-light.png`（长 Prompt 编辑页）+ COMPOSER-SPEC §8
- 改动（`ConversationScreen.kt` 的 `LongPromptEditor`）：
  1. 顶栏：**← 矢量返回** + 标题 **`长 Prompt 编辑`** + 副标题 **`Pi · 项目 · 草稿已保存`** + 右上 `完成编辑`
  2. 标题下加 1dp 分隔线（原型同款）
  3. 顶部提示：`在这里可以粘贴较长的代码或错误堆栈；确认无误后再发送。`
  4. 正文放进**带 1dp 边框、16dp 圆角的 Surface** 内（原型同款），去掉 TextField 自带描边避免双边框
  5. 底部信息行：左 **`N 字符`**、右 **`草稿已保存`**
  6. 右下 **`完成编辑`** 实心按钮（≥48dp）
  7. 底部说明：`语音仅作为转文字草稿，绝不自动发送。`
  8. **不提供发送按钮**：`完成`= 写回本地草稿，不触发发送（任务书 §UX-04b 明文；原型里的「发送」与之冲突，按规格取后者，并在此记录）
- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (28 tests)**
  - 更新 1 处断言：`3 字符（草稿会自动保存）` → `3 字符` + `长 Prompt 编辑`
- 截图（**真机** `192.168.1.8:39527` / 393dp / zh / Light）：`docs/evidence/real-long-editor.png`
  - 可见：`←` / `长 Prompt 编辑` / `Pi · spike-sandbox · 草稿已保存` / `完成编辑` / 提示文案 / 带边框编辑区 / `0 字符` 与 `草稿已保存` / `完成编辑` 按钮 / 语音说明
- 与原型仍有差异（如实列出）：
  1. 原型右下是**发送**按钮，我们按 COMPOSER-SPEC 只放**完成编辑**（不发送）
  2. 原型顶部右侧是 `⋯`，我们是文字 `完成编辑`
  3. 原型字符数有千分位格式（`129`），我们直接数字
- 回滚：`git revert <this commit>`
