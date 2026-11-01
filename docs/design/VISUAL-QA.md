# VISUAL-QA — M1 真机视觉验收

> 设备：Redmi 22021211RC / Android 14 / 1080×2400 / 120 Hz。构建：debug + release（均真机安装运行，**未使用 Compose Preview 代替**）。

## 1. 截图清单

| 文件 | 内容 | 主题 |
| --- | --- | --- |
| `docs/evidence/m1-10-board-dark.png` | 看板（分区 + 紧凑行 + 底部导航） | 深色 |
| `docs/evidence/m1-13-board-light.png` | 看板 | 浅色 |
| `docs/evidence/m1-11-conversation-dark.png` | 对话（用户气泡/assistant/折叠行/Composer） | 深色 |
| `docs/evidence/m1-14-conversation-light.png` | 对话 | 浅色 |
| `docs/evidence/m1-12-techsheet-dark.png` | 技术信息 Sheet（字段 + 复制） | 深色 |

## 2. 自检结果

| 检查项 | 结果 | 备注 |
| --- | --- | --- |
| 字体层级 | ✅ | 标题 24 / 分区 17 / 正文 16 / 辅助 11–13 |
| 边距与对齐 | ✅ | 页面 16 dp；卡片内 16 dp；行 64–76 dp |
| 内容截断 | ✅ | 标题单行省略；路径两行省略；技术字段可复制 |
| 键盘遮挡 | ✅ | Composer `imePadding`，发送键在键盘上方（I-65 历史修复） |
| 组件密度 | ✅ | 2 条紧凑行 + 分区头一屏可见；blocked 大卡占可读区 |
| 深色对比 | ✅ | 采用报告实测对比度（正文 15.74:1、状态最低 5.41:1） |
| 状态可理解 | ⚠️ | 状态点 + 文案；但**紧凑行未显示状态文字**（只有点），扫视依赖颜色 → 待 M2 加文字或图标 |
| 空/加载/错误态 | ✅ | 看板空态列出“没有主机/缺私钥/连接失败”；对话有 loading/error/empty |
| 返回导航 | ✅ | 对话→看板、Sheet 关闭；根不拦截 |
| TalkBack | ⚠️ | 卡片/行有 contentDescription；**状态点未合并朗读**（M2） |

## 3. 本轮发现并修复的视觉问题

| 问题 | 现象 | 修复 |
| --- | --- | --- |
| 「其他」默认折叠 | 只有 idle agent 时首屏像空白 | 默认展开（`showOther = true`） |
| 标题重复 | `Pi · π - herdr-gui-research` | `displayTitle()` 去掉 `π - ` 前缀并去重 |
| 字形当图标 | `▦`/`⛭` 在部分字体下像乱码 | 换成 `▤`/`⚙`，并记录“正式图标集留 M4” |

## 4. 仍未达标的视觉问题（M2）

1. **assistant 正文是原始 Markdown**（`**粗体**`、反引号直接显示）→ M2 分级渲染；
2. **紧凑行缺少状态文字**，纯色点对色弱不友好 → 加 12sp 状态词或图标；
3. **项目/主机识别**：行内用 `w7 · herdr-gui-research`（workspace 编号 + 目录名），跨主机时需要更明确的主机简称；
4. **blocked 卡片无真实样本**（当前环境无 blocked agent）→ M2 用可控触发（pi 扩展/Claude hook）或测试 fixture（仅测试用途）验证；
5. **无 App 图标**（系统默认）→ M4。
