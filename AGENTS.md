# leetcode-editor（shuzijun/leetcode-editor 的個人 fork）

專案簡介見 `README.md`；自訂範本/登入說明見 `doc/`（正本在那，別在此摘要）。
本檔只記錄「讀 code / 跑 tree 看不出的決策與踩過的雷」——結構問題請直接讀 code。

## 平台版本 invariant（升級前必讀）
- build 目標鎖 IntelliJ **2025.3 / sinceBuild 253**。為什麼是 2025.3：本 plugin 用 JCEF，而 JCEF 從 2025.3 起才拆成獨立 bundled plugin `com.intellij.modules.jcef`（見下）；2025.2 沒有該 plugin、無法宣告依賴 → 2025.3 是「能宣告 JCEF 依賴又支援新版」的最低底線。
- 使用者實際用 **2026.2**，已用 `verifyPlugin` 驗證 Compatible（能跑）。build 目標停在 2025.3 而非 2026.x，是因為 2026.x 需 Kotlin 2.4+ metadata（要同步升 Kotlin）且有上游 #766（`getChildren` 破壞搜尋）——要升先解這兩點。

## ⚠️ 平台移除的 library（這個 2 年老 plugin 最大的坑）
IntelliJ 每年會把「曾在 platform core 的第三方 library」移除或搬進 bundled plugin。**這類問題編譯與單元測試都抓不到**（編譯期 classpath 有、runtime 的 plugin classloader 沒有），只有 `verifyPlugin` 或真跑 IDE 才爆（`NoClassDefFoundError`）。已處理：
- **JCEF**（`com.intellij.ui.jcef.*`）：2026.x 拆成 bundled plugin → `plugin.xml` 必須有 `<depends>com.intellij.modules.jcef</depends>`（拿掉 → 啟動崩潰）。
- **commons-collections 3.x**：2025.3+ 移除 → 已全換 JDK（`HashedMap`→`HashMap`、`CollectionUtils`→null/isEmpty 檢查）。別再 import `org.apache.commons.collections`。
- **apache-http**（`MultipartEntityBuilder`/`BasicClientCookie`，登入/cookie 用）：2025.3+ 移除 → 已在 `build.gradle.kts` bundle `httpmime`。別再賭 platform 提供。
- **通則**：新增任何第三方 import 或動平台 API 前，先確認該 class 在目標版本的 **platform core lib**（`$SDK/lib/`，不是 `plugins/*/lib/`）——不在就換 JDK 或 bundle 進 plugin。

## 其他 edit-time invariant（別退回）
- `HttpRequestUtils.MyExecutorHttp` 用 `newDefaultHttpClient(10,30,30)` 覆蓋 lc-sdk 0.0.3 的預設 timeout（600s/1800s，慢連線會掛半小時）。別移除。
- HTTP 快取 `maximumWeight(32MB)` + 用 `HttpRequest` 物件當 Guava key + `cacheParam(null)` 不開快取（上游 #763）。別改回 `maximumSize` 或 hashCode 字串 key。

## 驗證是必備的（編譯 / test 綠 ≠ 能跑）
- **任何改動、發版前，一律跑完整鏈**：`./gradlew test buildPlugin verifyPlugin`；最省事用 `scripts/release.sh --verify`（見 skill `release-plugin`）。**別只靠 compileJava/test 就宣稱修好**——這次 JCEF/commons-collections/apache-http 三連崩，全部是 compileJava + test 綠、卻一裝就崩。
- 各層抓的東西不同：`compileJava` 只驗符號存在；`test` 只驗純邏輯（`model/`/`utils/`）；`buildPlugin` 內的 `buildSearchableOptions` 會啟動 headless IDE 載入 plugin（比純編譯接近 runtime）；**`verifyPlugin` 抓 API/module 依賴相容性**——但對「library 從 core 移除」仍會漏報（曾對 commons-collections 報 Compatible 卻 runtime 崩），它綠不代表 100% 沒事。
- headless 死角：`runIde` 的 IU 需付費授權，且無頭環境測不到「tool window 的 GUI 觸發」與「需真帳號的登入路徑」。這兩塊只能在有授權的 IDE 裝 zip 手動驗。

## 建置地雷
- `buildPlugin` 內含 `buildSearchableOptions`，需**獨佔一個 IDE 實例**；殘留報 `Only one instance of IDEA can be run at a time`（環境問題非程式碼）：`pkill -9 -f ideaIU` 後重跑（`scripts/release.sh` 已內建）。

## 其他
- `leetcode.cn` 匿名被 Cloudflare 擋（curl/單元測試打不通），只有 JCEF 登入後可用；`leetcode.com` GraphQL 正常。
- 發版：`scripts/release.sh [--verify] [--push]`，別手組 shell。
