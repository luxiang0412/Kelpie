# 归档（archive）—— 只读，不再演进

| 目录 | 是什么 | 为什么在这里 |
| --- | --- | --- |
| `archive/android/` | 旧版 Android 客户端 0.1.x（包名 `dev.herdr.app`；`core:{ssh,herdr,data}` + `feature:board` + `app` + `benchmark`） | 2026-11-02 起被 `signal/` 重写取代；`signal/core/{ssh,herdr,markdown}` 最初就是从这里复制的。2026-11-03 移到这里 |
| `archive/spike/` | 早期技术验证工程（`jvm/` 命令行探针 + `android/` 探针 App） | 能力早已在客户端里重写；保留仅为追溯 |

规则：

1. 归档内容**不再修改**，也不作为任何结论的依据。
2. 这里的工程不保证还能构建（依赖版本、路径都没再维护）；构建产物不入库。
3. 旧版的文档（设计规格、过程报告、截图证据、台账）没有归档，而是已经删除，需要时从 git 历史取：`git show signal-v0.2.0:<路径>`。
   Signal 代码里还在引用的旧决策 / 旧问题，摘录在 `docs/signal/BACKGROUND.md`。
