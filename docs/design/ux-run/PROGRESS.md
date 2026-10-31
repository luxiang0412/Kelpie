# UX-D0 执行进度

状态：`TODO / IN_PROGRESS / PASS / PARTIAL / BLOCKED / SKIPPED`
分支：`ux/graphite-editorial`（未合并 main）｜基线 `f56430c`
结论等级：**`FUNCTIONAL_PARTIAL`**（12 PASS / 6 PARTIAL / 1 SKIPPED）

| Task | 状态 | 说明 | Commit |
| --- | --- | --- | --- |
| UX-00 仓库审计与基线 | PASS | 设计→源码映射、基线环境、进度表 | `704365c` |
| UX-01a 对话顶栏大字体裁剪（I-93） | PASS | 内容自适应头；真机 fontScale 1.3 截图 | `48a94b1` |
| UX-01b InlineCode 保真 | PASS | 移除空格伪 padding；2 项 UI 断言 | `128632a` |
| UX-00a Tokens/对比度/图标契约 | PASS | 色值对齐 §2.2；`ContrastTest` 34 断言；4 个矢量图标；字形图标清零 | `6b10196` `7cc5112` |
| FIX-01 未捕获 SSH 异常致崩溃 | PARTIAL | handler + runCatching 已加；**设备侧未复验** | `7cc5112` |
| UX-02a 工作台顶栏/主机选择器/导航 | PASS | 主机选择器下移（BOARD-HOST-001，PROVISIONAL）；窄屏换行；真机 P1/P2 + AVD P5/P6 | `e19e731` |
| UX-02b Attention First / done 折叠 | PASS | `BOARD-DONE-001` 落实；blocked 前 2 卡；单一语义色；idle/unknown 分区 | `309e3a1` |
| UX-02c 单条 Banner / Unknown 入口 | PASS | 优先级 Banner；工作台可直接进 Unknown 详情；`BoardBannerTest` 5 项 | `8d4d3ee` |
| UX-03a 对话正文/身份头/气泡 | PASS | 分组表头、bodyReading 16/25、用户气泡 86%、围栏预览闭合、短文本同步解析 | `edfaf40` |
| UX-03b Markdown/CodeBlock 视觉 | PASS | 引用 12dp、列表悬挂缩进、code 13/20、复制取源文 | `62d03a1` |
| UX-03c Tool/Thinking/长输出 | PASS | 移除「失败」盲猜；状态仅 运行中/已返回；52dp 头部；不放假「查看全文」 | `62d03a1` |
| UX-03d 复制/系统选择/更多 | PASS | `SelectionContainer` + 复制/更多操作条（CONV-ACTIONS-001）；3 项 UI 测试 | `ddb2fb8` |
| UX-03e 阅读位置/新消息入口 | PARTIAL | 四状态 + 锚点(paneId-index+offset) + 未读；**`Resyncing` 未做、手势未实测** | `22c7a35` |
| UX-04a Composer 轻量双层 Surface | PASS | surface + 20/18dp 圆角 + 16/12dp 边距；无假 Mic/附件 | `017a24e` |
| UX-04b 长 Prompt/草稿/发送异常 | PASS | 长编辑「取消」回退；完成只写草稿；三态不回归 | `8f580ce` |
| UX-04c 引用消息 → Composer | PARTIAL | 追加式引用 + 8000 上限拒绝 + 不覆盖草稿；**光标处插入未做** | `8f580ce` |
| UX-05a i18n（zh/en） | PARTIAL | 46 key 双语 + 三屏接线 + 测试去中文化；**StatusText/Hosts/Sheet/通知未接线** | `63b0533` `87edec2` |
| UX-05b App 内语言设置 | PARTIAL | API33+ `LocaleManager` 三档；<33 明确禁用；**切换生效与状态保留未验证** | `f03420f` |
| UX-06 会话内搜索 | PARTIAL | 只交付数据边界审计 + 实施计划（`UX-06-PLAN.md`）；**未加搜索 UI** | `42b8c6b` |
| UX-07a STT Spike | PASS | 研究结论：不实现；降级系统键盘语音；无 `RECORD_AUDIO` | `cc55234` |
| UX-07b STT 实施 | SKIPPED | 门槛未满足（无已验证本地识别路径） | — |
| UX-08a 主机/设置视觉一致性 | PARTIAL | Hosts 页可滚动（修复设置项不可达）；**分组与文案未做** | `cc55234` `42b8c6b` |
| UX-08b 空态/异常态/Motion | PARTIAL | 审计为主；统一 `EmptyState`、搜索/STT 空态未做 | `9c00752` |
| UX-08c 品牌图标候选 | PASS（PROPOSAL） | 3 个 Focus Orbit 方向；**未替换资产** | `cc55234` |
| UX-09 全局 QA | PARTIAL | 决策表核对 + 回归 + 隐私检查；真机项未验证 | `9c00752` `8414981` |

## 全局测试状态（最近一次实际执行）

| 命令 | 结果 |
| --- | --- |
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:assembleDebugAndroidTest` | BUILD SUCCESSFUL |
| `:feature:board:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:core:data:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:core:herdr:test` | BUILD SUCCESSFUL |
| AVD `emulator-5556` 全量 UI | **OK (26 tests)**（en-US 环境下） |
| `:core:data` `DraftMigrationTest` | OK (1 test) |

## 仍未完成（下一轮从这里开始）

1. UX-03e：`Resyncing` 状态 + 手势实测（上滑不被拉回、点提示回底部）
2. UX-04c：光标处插入（需 `TextFieldValue` 改造）
3. UX-05a：`StatusText`/`HostsScreen`/`TechnicalInfoSheet`/通知的 i18n + 未翻译 key 检查脚本
4. UX-05b：语言切换生效与草稿/Unknown/锚点保留验证
5. UX-06：搜索 UI 实现（按 `UX-06-PLAN.md`）
6. UX-08a：设置分组（外观与语言 / 通知与后台 / 连接与安全 / 隐私与数据 / 关于）
7. UX-08b：统一 `EmptyState` 组件 + 搜索/STT 空态
8. 真机：P1–P8 全量截图、TalkBack、中文 IME、手势导航 Insets、`reduce motion`
9. FIX-01：SSH 崩溃修复的设备侧复现（需 USB adb 或 airplane-mode 方案）
