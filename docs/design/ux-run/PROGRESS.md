# UX-D0 执行进度

状态：`PASS / PARTIAL / SKIPPED`｜分支 `ux/graphite-editorial`（未合并 main）
结论等级：**FUNCTIONAL_PARTIAL**

| Task | 状态 | 说明 |
| --- | --- | --- |
| UX-00 审计与基线 | PASS | 设计→源码映射、基线、进度表 |
| UX-00 文档清单更正 | PASS | 6/6 规格全部存在并已读全（此前误判缺失，已更正） |
| UX-01a 顶栏大字体裁剪 | PASS | 内容自适应头；fontScale 1.3 截图 |
| UX-01b InlineCode 保真 | PASS | 去除空格伪 padding；2 项 UI 断言 |
| UX-00a Tokens | PASS | error/focus token、attention+dark 全色值、字阶、mdH1–3、card 16dp；对比度实测 |
| UX-02a 工作台顶栏/主机选择器 | PASS | 主机选择器下移、窄屏换行、1dp 分隔线 |
| UX-02b Attention First | PASS | done 默认收起、blocked 前 2 卡、单一语义色、idle/unknown 分区 |
| UX-02c Banner 优先级 | PASS | 安全>Unknown>认证>连接>缓存 + 全部问题入口 |
| UX-03a 对话排版 | PASS | 气泡 88/86/520、13sp 标识、Thinking 可选、Unknown 降级 |
| UX-03b Markdown | PASS | 12dp 块距、标题 20/8、列表 4dp、引用 2dp、mdH1–3 |
| UX-03c Tool 卡 | PASS | 容器+边框+图标+参数/命令/输出+复制+全量阅读页；无嵌套滚动 |
| UX-03d 复制/更多/系统选择 | PASS | SelectionContainer + 复制/更多 |
| UX-03e 阅读位置 | PARTIAL | 五状态(含 Resyncing)+锚点+未读；手势未实测 |
| UX-04a Composer 视觉 | PASS | 双层 Surface、2dp focus ring、40dp 圆 Send |
| UX-04b 发送态/离线/记录 | PARTIAL | 三态文案、离线禁 Send、查看发送记录、IdentityUncertain、720dp；草稿归属选择未做 |
| UX-05a i18n | PARTIAL | 术语/Hosts 33 条/Sheet/搜索/工具卡已本地化；通知与部分 contentDescription 未做 |
| UX-05b 语言设置 | PARTIAL | API33+ LocaleManager；<33 禁用；切换后状态保留未验证 |
| UX-06 会话内搜索 | PARTIAL | 已实现定位与边界标注；命中高亮与搜索 UI 测试未做 |
| UX-07a STT Spike | PASS | 结论文档；不实现，降级系统键盘语音 |
| UX-07b STT 实施 | SKIPPED | 门槛未满足 |
| UX-08a Hosts/设置 | PARTIAL | 自适应顶栏+两个分组+空态可达；其余分组未做 |
| UX-08b 空态/动效 | PARTIAL | 共享 EmptyState（看板）；reduce-motion 接入折叠动画；对话/Hosts 空态未迁移 |
| UX-08c 品牌图标 | PASS(PROPOSAL) | 3 个 Focus Orbit 方向，未替换资产 |
| UX-09 全局 QA | PARTIAL | P1/P2/P5/P8 AVD 截图 + 原型对照 + 自检表；真机与 P3/P4/P6/P7 未采集 |

## 测试现状（最近一次）
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` BUILD SUCCESSFUL
- AVD `emulator-5556` Compose UI：**OK (26 tests)**
- `:feature:board` / `:core:data` / `:core:herdr` JVM：全绿（含 ContrastTest 38 断言）

## Claude 适配（方案 B + C）

- 方案 B（`5a53ae7`）：`ClaudeTranscriptParser` + 按路径分发。
- 方案 C（`da5afca`）：`ClaudeSessionLocator`（纯逻辑）+ `ClaudeSessionResolver`（只读 SFTP 定位）。
- **真机 E2E 首发通过**：`docs/design/ux-run/ADAPT-CLAUDE-E2E.md`。修复 C-1（缓存丢 `agentSessionId`）、C-2（`hasTranscript` 判据）、C-3（回填窗口切掉首条 user 消息）。

## 缺口补齐：GFM 表格（I-99）

`MdBlock.Table` + `TableBlock`（表头/分隔线/列等分），`PARSER_VERSION` 2→3；单测 `MarkdownTableTest` 6 项，真机浅/深色截图（`spike/results/table1.png`、`table-dark.png`）。
