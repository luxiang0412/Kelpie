# herdr 0.9.3 schema 摘要（自动生成，证据文件）

- 来源：本机 `herdr api schema --json`（herdr 0.9.3, Windows）
- protocol = 22, schema_version = 1
- 方法数 = 102

## 写操作 / 读屏参数（无前置条件、无幂等键）

| method | params |
| --- | --- |
| `agent.prompt` | `target`, `text`, `wait` |
| `agent.wait` | `target`, `timeout_ms`, `until` |
| `pane.send_keys` | `keys`, `pane_id` |
| `pane.send_text` | `pane_id`, `text` |
| `pane.read` | `format`, `lines`, `pane_id`, `source`, `strip_ansi` |

## 订阅条目必填字段

| type | required | 其它字段 |
| --- | --- | --- |
| `workspace.created` | type | - |
| `workspace.updated` | type | - |
| `workspace.metadata_updated` | type | - |
| `workspace.renamed` | type | - |
| `workspace.moved` | type | - |
| `workspace.reordered` | type | - |
| `workspace.closed` | type | - |
| `workspace.focused` | type | - |
| `worktree.created` | type | - |
| `worktree.opened` | type | - |
| `worktree.removed` | type | - |
| `tab.created` | type | - |
| `tab.closed` | type | - |
| `tab.focused` | type | - |
| `tab.renamed` | type | - |
| `tab.moved` | type | - |
| `pane.created` | type | - |
| `pane.closed` | type | - |
| `pane.updated` | type | - |
| `pane.focused` | type | - |
| `pane.moved` | type | - |
| `pane.exited` | type | - |
| `pane.agent_detected` | type | - |
| `pane.output_matched` | type, pane_id, source, match | lines, match, pane_id, source, strip_ansi |
| `pane.agent_status_changed` | type, pane_id | agent_status, pane_id |
| `pane.scroll_changed` | type, pane_id | pane_id |
| `layout.updated` | type | - |

## ping.capabilities（特性开关，不是方法清单）

- `detached_server_daemon`: boolean
- `endpoint_protocol_generation`: ['integer', 'null'] — Stable client-owned endpoint generation supported by this server.
- `health_check`: boolean — Whether this server supports endpoint health probes.
- `live_handoff`: boolean
- `ssh_agent_registration`: boolean — Supports connection-scoped `server.ssh_agent.register` on the local JSON API.
- `surface_interest`: boolean — Whether this server supports explicit client-shell surface interest.

## 错误码

`error_response.$defs.ErrorBody` 只有 `code: string` + `message: string`，**没有枚举**。

- `events_lost` 在 schema 中出现次数：0
- `agent_blocked` 在 schema 中出现次数：0
- `presence` 在 schema 中出现次数：0
- `lease` 在 schema 中出现次数：9
- `ownership` 在 schema 中出现次数：0
- `owner` 在 schema 中出现次数：2
- `lock` 在 schema 中出现次数：8

结论：`events_lost` / `agent_blocked` 是运行时错误码（见文档），不在 schema 枚举中；
协作原语（presence/lease/owner/lock）在 102 个方法里完全不存在。
