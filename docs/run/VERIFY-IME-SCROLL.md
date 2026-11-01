# 验证 #9 — IME 弹出时不改变阅读位置（DESIGN-SYSTEM §9.2 / CONVERSATION-SPEC §9.1）

- 设备：Redmi 22021211RC（真机 `192.168.1.8:39527`），zh，Light，393dp
- 步骤：
  1. 打开真实会话（`Pi · spike-sandbox`），**上滑浏览到历史位置**（不是底部）
  2. 点击 Composer 输入框
  3. 截图对比
- 证据：
  - `docs/evidence/ime-before.png`：上滑后的历史位置（可见 `reply with just GLOBAL` / `GLOBAL` / `你能做什么?` / `思考过程 138 字符`）
  - `docs/evidence/ime-after.png`：IME 弹出后**同一批消息仍在原位**，列表没有被拉到底部
  - `dumpsys input_method` → `mInputShown=true`（确认键盘确实弹出）
- 结论（对照规格）：
  | 规格 | 结果 |
  | --- | --- |
  | §9.2「用户浏览历史时 IME 弹出优先保持可见阅读锚点」 | ✅ 未跳底 |
  | §3.3「聚焦 1–2dp Accent Focus Ring」 | ✅ 截图可见 2dp 蓝色环（单层，无三重描边） |
  | §3.1「Composer 底部距系统导航由 WindowInsets 决定」「禁止双倍留白」 | ✅ Composer 紧贴键盘，无额外空白 |
  | §3.1「输入 1–5 行自适应」 | 本次为单行输入态 |
- 未覆盖：中文输入法**组合输入**（拼音未上屏时点发送）、键盘收起后的位置恢复。
