# UX-08a report — 主机管理 / 设置基础视觉一致性（PARTIAL）

- baseline：`cc55234` / `ux/graphite-editorial`
- inspected：`HostsScreen.kt`（顶部 `TopAppBar` + 后台监控 Switch + 主机列表 + 新增/编辑对话框）、`TechnicalInfoSheet.kt`
- goal：让非三屏页面不再像 Debug 表单；不加新 SSH 能力
- changed（本轮只做最小安全改动）：
  - Hosts 页内容改为**可滚动**（此前空态占满视口，导致「后台监控」「语言」等设置项在无主机时**不可达**——可用性缺陷）
  - 底部留 24dp，避免最后一项贴到导航栏
- 未改动：主机新增/编辑/删除的真实流程、密钥导入、二次确认、连接/断开
- tests：`:app:assembleDebug` OK
- 四类结论：
  - **已实现并验证**：Hosts 页可滚动（编译 + 截图）
  - **已实现但未验证**：设置项分组（外观与语言 / 通知与后台 / 连接与安全 / 隐私与数据 / 关于）**未实现**；
    Hosts 页文案（"主机""返回""添加""还没有主机""添加主机""后台监控（常驻通知）"…）**仍为硬编码中文**，未接 `stringResource`
  - **未实现**：Settings 分组页；TechnicalInfoSheet 的诊断字段分组与「待确认写操作」入口的进一步整合（该入口已在 UX-02c 加到工作台 Banner + Sheet）
  - **已知问题**：无主机时滚动手感未经真机验证；Hosts 页在 en 语言下会混排中英文（UX-05a 遗留）
- commit：见提交（与 UX-07a/08c 文档同批）
