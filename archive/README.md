# 归档（archive）— 只读，不再演进

| 目录 | 是什么 | 为什么在这里 |
| --- | --- | --- |
| `archive/spike/` | 早期技术验证工程（`jvm/` 命令行探针 + `android/` 探针 App，27 个源文件） | 能力已在 `android/core/{ssh,herdr,data}` 重写并强化；两份代码无调用关系，保留仅为追溯结论 |
| `archive/docs/` | 过期/被取代文档：spec v0.1（board/conversation/composer）、PHASE0-SUMMARY、STATUS-FOR-REVIEW、UX-D0-README-IMPORT、外部评审简报两份、UX-01a 任务包草稿、UX 设计评审输入 | v0.2 规格与现行报告已取代它们；保留上下文，避免误当现行规格引用 |

规则：
1. 归档内容**不再修改**，也不作为任何结论的依据；
2. 现行规格只有 `docs/design/`（v0.2+）、台账只有根目录 4 份活文档；
3. `archive/spike/results/` 的历史截图/日志**不进 git**，只留本地。
