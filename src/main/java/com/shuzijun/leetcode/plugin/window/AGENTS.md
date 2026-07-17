# window — 工具視窗 UI

> 共通見 repo 根 `AGENTS.md`，本檔只寫本模組增量。

ToolWindow 面板：題目導覽（三種瀏覽模式）、結果 console、登入。

## 業務地圖
- `WindowFactory` — ToolWindow 入口。
- `NavigatorTabsPanel` — 三個 navigator tab 的容器；持有跨面板 user 快取。
- `navigator/` — 三種題目瀏覽，各配一個 `*Table`：
  - `NavigatorPanel` + `NavigatorTable` — **page**（伺服器分頁）
  - `AllNavigatorPanel` + `AllNavigatorTable` — **all**（本地全量過濾，見踩雷）
  - `TopNavigatorPanel` + `TopNavigatorTable` — **codeTop**（公司題頻分頁）
- `NavigatorTableData` — 三個 table 的共用 model 基類（增量更新邏輯在這）。
- `ConsolePanel` / `ConsoleWindowFactory` — 執行/提交結果輸出。 `ProgressPanel` — 刷題進度統計。
- `login/` — JCEF 登入（`HttpLogin` / `LoginPanel`）。 `dialog/` — Solution/Submissions/Testcase 彈窗。

## 進來改要遵守
- 所有 UI 更新走 EDT（`invokeLater`）；資料組裝（`buildDataVector`）放背景緒，EDT 只 `setDataVector`。
- 單一題目狀態變更用 `refreshRow(row)` 更新該列，**別**呼叫整表 `updateData`/重建（all 分頁重建 = 3000 列）。

## 踩雷
- **all 分頁沒有真正的伺服器分頁**：`loadAllServiceData` 在記憶體對 ~3000 題全量過濾/排序。動 filter/sort 效能都壓在這，別讓它更重。
- `NavigatorTabsPanel.DisposableMap.getOtherKey` 曾把自己的 key 回傳（跨面板 user 快取永遠失效）——已修，別退回。

細節查 codegraph。
