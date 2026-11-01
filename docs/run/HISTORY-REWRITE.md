# 仓库历史重写记录（2026-11-01）

## 为什么要做

`.git` 里 95% 的体积来自历史截图：`spike/results/` 曾跟踪 209 张 PNG / 145 MB，
其中 131 张在重构（P2）后已无任何文档引用。

## 做了什么

1. **备份**：`../herdr-gui-full-backup-20261101.bundle`（144 MB，含改写前全部 refs）。
   恢复方式：`git clone ../herdr-gui-full-backup-20261101.bundle restored/`
2. **剔除历史路径**：`git filter-repo --invert-paths --path spike/results/ --path archive/spike/results/`
3. **校验**：改写前后 `HEAD^{tree}` 都是 `0bbbb95ed7280b567b69ef1524a754d9250f5d1b`
   → 工作区内容**逐字节一致**；跟踪文件 379 个；`docs/evidence` 92 个文件完好。
4. **体积**：`.git` 154 MB → **78 MB**。

## 代价（必须知道）

- **所有 commit hash 变了**（HEAD `c922dcb` → `d51654d`），任何已 clone 的副本都要重拉；
- `main` 的本地 hash 也被改写（`f56430c` → `eee74f0`）；**不要**推 main，需要旧 main 从备份 bundle 取；
- `filter-repo` 会移除 `origin`，已手动加回，但**尚未推送**（强推需单独确认）。

## 为什么还有 78 MB

因为 `docs/evidence/` 里 92 份**当前仍被引用**的证据合计 76 MB（多为 1080×2400 原始 PNG，单张 ~3 MB）。
要进一步压缩，可对这些 PNG 做无损/调色板优化（预计 78 MB → 15–25 MB），属于**改动证据像素**的操作，
需单独决策（改完要再跑一次 `filter-repo` 才能把旧 blob 从历史里清掉）。
