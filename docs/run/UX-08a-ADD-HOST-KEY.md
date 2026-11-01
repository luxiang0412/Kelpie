# UX-08a 增补 — 新增主机时可一次配完私钥

- 背景：用户问「主机添加？需要提前先配置 ssh key？」→ 原流程是「先存主机 → 再点卡片上的『私钥』」两步，容易漏。
- 改动（`HostsScreen.kt`）：
  1. 新增/编辑对话框增加 **「私钥（可选）」** 区：标签 + 说明（仅写 app 私有目录 0600、不外传、编辑时留空表示不修改）+ 等宽输入框（placeholder 显示 `-----BEGIN OPENSSH PRIVATE KEY-----`）
  2. 保存逻辑：**先导入私钥、成功后再保存主机**；私钥失败则在对话框内显示错误并**不关闭对话框**（避免"主机已存在但私钥没写成"的半状态）
  3. 编辑已有主机时留空 → 保持原有私钥不变
- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (28 tests)**
- 截图（真机 `192.168.1.8:39527` / zh / Light）：`docs/evidence/host-add-dialog.png`
  - 字段顺序：名称 / 主机地址 / 端口+用户名 / herdr 会话 / herdr 绝对路径 / **私钥（可选）+ 说明 + 输入框**
- 与文档：`DESIGN-SYSTEM §7.2`（权限/说明文案要本地化、讲清用途）；未新增假入口（私钥本来就是真实能力）
- 未做：从文件选择私钥（SAF）、带 passphrase 的私钥（`loadKeys(path, passphrase)` 需新增口令处理，涉及安全模型，等确认）
- 回滚：`git revert <this commit>`
