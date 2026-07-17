# leetcode-editor（shuzijun/leetcode-editor 的個人 fork）

專案簡介見 `README.md`；自訂範本/登入說明見 `doc/`（正本在那，別在此摘要）。
本檔只記錄「讀 code / 跑 tree 看不出的決策與踩過的雷」——結構問題請直接讀 code。

## 平台版本 invariant（升級前必讀）
- 刻意鎖定 IntelliJ **2025.2 / sinceBuild 252**（對齊官方 intellij-platform-plugin-template）。
- **別升到 2026.x**：2026.1 引入 `getChildren(null)` 破壞搜尋（上游 issue #766 未解），且平台改用 Kotlin 2.4+ metadata、需同步升 Kotlin 才編得動。要升先解這兩點。

## 建置地雷
- `./gradlew buildPlugin` 內含 `buildSearchableOptions`，它需**獨佔一個 IDE 實例**。若上一輪 `runIde` 或 build 的 IDE 進程殘留，會報 `Only one instance of IDEA can be run at a time`——這是環境問題非程式碼缺陷：`pkill -9 -f ideaIU` 清掉殘留後重跑即綠。

## edit-time invariant（別退回）
- `HttpRequestUtils.MyExecutorHttp` 刻意用 `newDefaultHttpClient(10, 30, 30)` 覆蓋 lc-sdk 0.0.3 的預設 timeout（connect 600s / read+write 1800s，慢連線會掛半小時）。別移除這個覆蓋。
- HTTP 回應快取用 `maximumWeight(32MB)` 以記憶體量計上限、用 `HttpRequest` 物件當 Guava key（靠 `equals` 防 hashCode 碰撞）；`cacheParam(null)` 一律不開快取（上游 #763 根因，`HttpRequest`/`Graphql` 內有註解）。別改回 `maximumSize` 或 hashCode 字串 key。

## 測試覆蓋現況（別誤判有保護）
- 測試**只覆蓋純邏輯層**（`model/` `utils/`，例如 `HttpRequestUtilsCacheTest` 驗快取 load-once/非200不快取/碰撞防護）。
- 預覽面板（`editor/converge/*Preview`）、EDT/執行緒、JCEF、真實網路**沒有自動化測試**。動這些只能靠 code review + `runIde` 手動驗，別假設有測試網接住。

## 驗證邊界
- `leetcode.cn` 匿名請求被 Cloudflare 擋（curl/單元測試打不通），只有 JCEF 登入後可用；`leetcode.com` GraphQL 正常。
- `runIde` 跑的是 IU，需付費授權；無頭環境只驗得到「外掛載入成功」，登入/刷題全流程必須在有授權的 IDE 手動裝 zip 驗。

## 常用指令
- 驗證（含打包）：`./gradlew test buildPlugin` → 產出 `build/distributions/leetcode-editor-*.zip`
- 只跑測試：`./gradlew test`
- 實機啟動：`./gradlew runIde`
