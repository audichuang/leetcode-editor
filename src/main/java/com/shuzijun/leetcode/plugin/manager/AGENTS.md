# manager — 業務邏輯層

> 共通見 repo 根 `AGENTS.md`（快取/HTTP invariant、版本鎖），本檔只寫本模組增量。

被 `window/` `actions/` 呼叫，透過 `utils/HttpRequestUtils` + `model/Graphql` 打 LeetCode，把回應轉成 `model/` 物件。

## 業務地圖（各類負責什麼）
- `QuestionManager` — 題目抓取：`getQuestionViewList`(分頁) / `getQuestionAllService`(全量題庫，2 天快取) / `getQuestionIndex`(定位) + `invalidateAll`(清所有 domain 快取)。**核心入口，先看它**。
- `CodeManager` — 開題目碼檔/內容、`SubmitCode`/`RunCodeCode`（提交/執行 + 背景輪詢結果）。
- `ArticleManager` — 題解：`getSolutionList`（4 頁平行抓）、`openArticle`。
- `SubmissionManager` — 歷史提交紀錄。 `NoteManager` — 筆記 show/pull/push。
- `SessionManager` — 題單 session 列表 / `switchSession`。 `FindManager` — 篩選標籤（難度/狀態/tags/lists）。 `FavoriteManager` — 收藏加/removed。
- `ViewManager` — 把上述 manager 的資料組進 UI（`loadServiceData` 分頁 / `loadAllServiceData` 全量 / `pick` 隨機）。 `CodeTopManager` — codeTop 分頁專用。
- `NavigatorAction` — 這是 UI 契約 **interface**（不是 manager），window 面板實作它。

## 進來改要遵守
- 切 session（`ProgressAction`）、Refresh、登入/登出後，必須清 **domain 快取**（`QuestionManager.invalidateAll()`）+ HTTP 快取，否則 All 分頁會顯示舊狀態達 2 天。
- 帳號可能中途切換：`getQuestionAllService` 有 generation 屏障，別移除。

## 踩雷
- submit 成功後**只**清 HTTP 快取 + 靠 `QuestionStatusNotifier` 即時更新該列，**不清**全量 `questionAllCache`（否則每次提交都重抓 ~2MB）。

細節查 codegraph。
