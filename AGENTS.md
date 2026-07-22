# leetcode-editor（shuzijun/leetcode-editor 的個人 fork）

專案簡介見 `README.md`；自訂範本/登入說明見 `doc/`（正本在那，別在此摘要）。
本檔只記錄「讀 code / 跑 tree 看不出的決策與踩過的雷」——結構問題請直接讀 code。

## 平台版本 invariant（升級前必讀）
- build 目標鎖 IntelliJ **2026.2 / sinceBuild 262**，`untilBuild` 留空。使用者實際用 2026.2，已用 `verifyPlugin` 對 `create("IU","2026.2")` 驗證 Compatible。
- 202x 系列升級鏈：2025.3 是「能宣告 JCEF bundled-plugin 依賴」的最低底線（見下一節，這條沒變，JCEF 依賴仍要留著）；253→262 之間原本擋路的兩個閘門都已解掉，別再回退：
  - **Kotlin metadata**：2026.x 平台需要 Kotlin 2.4+ metadata → `gradle/libs.versions.toml` 的 kotlin plugin 已升到 2.4.10（同時 `intelliJPlatform` gradle plugin 升到 2.18.1、`jvmToolchain` 21→25，因為 2026.2 platform jar 是 Java 25 bytecode）。
  - **上游 #766**（2026.x `getChildren` 破壞 tool window 搜尋）：已在 `FindActionGroup`（BGT + `e.getData()` + `getChildren(null)` 防護，檔內有詳細註解）修好，且同款 BGT-safe 存取模式已推廣到其餘曾用 `WindowFactory` 舊版直接查找 helper（該 helper 已移除）的 action/manager。**這是不可回退的既有成果，別動 `FindActionGroup` 那段邏輯。**
- `gradle.properties` 另加 `kotlin.stdlib.default.dependency=false`（平台已內建 Kotlin stdlib，不重複打包）；gradle wrapper 已是 9.5.0，未來升級不必再動。
- `build.gradle.kts` 的 `pluginVerification.ides` 只留 `create("IU","2026.2")` 這個 explicit pin，**不加 `recommended()`**：sinceBuild=262 之後兩者已撞同一顆 build（實測 verifyPlugin 只跑「1 of 1 verifications」），explicit pin 才是「使用者實際平台」的固定錨點，`recommended()` 是動態的、以後會悄悄不再測到這個底線。

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

## verifyPlugin 已知殘留與保留原因（2026.2、IU-262.8665.258 實測）
數字一律區分「source sites」（原始碼裡幾個相異呼叫點）與「verifier records」（`report.html`/`*.txt` 裡列的違規筆數，同一呼叫點若同時觸發兩種違規類型會算兩筆）——兩者常對不上，別混用。本輪（Task 1~8）之前的舊基準是 23 筆 scheduled-for-removal（SFR）／16 筆 deprecated／10 筆 internal／1 筆 non-extendable／2 筆 override-only（皆 verifier records）；升級+清理後實測：
- **SFR：23 → 2**（verifier records，2 source sites）。原 23 筆全部是 `HttpConfigurable`/`IdeaWideProxySelector`/`NonStaticAuthenticator` 那叢集，已隨 proxy 網路棧改寫（`HttpRequestUtils`）清空。殘留 2 筆本輪未處理、原因見下方「本輪未驗證出替代 API」。
- **Deprecated：16 → 1**（verifier records，1 source site：`TextEditorWithPreview.MyFileEditorState` 建構子，`AbstractEditAction`）。原 16 筆含 `UiCompatibleDataProvider.getData(String)` override（8 筆，Task 2+4 消）、`MyDataContext` 相關（4 筆，Task 4 消）、`CredentialAttributes`/`deleteCookies` 舊多載（3 筆，Task 6 消）等。
- **Internal：10 → 9**（verifier records，8 source sites：`ErrorReportHandler.submit` 對 `AbstractMessage` 同時觸發「class 參照」與「方法呼叫」兩筆 record 但只有 1 個呼叫點）。Task 4 消掉 `MyDataContext` 那筆（10→9）；`PluginManagerCore.getPlugin` ×3 筆 gate 未過（見下），維持不變。
- **Non-extendable：1 → 0**。
- **Override-only：2 → 1**（verifier records，1 source site：`ConvergeProvider.getBuilderFromEditorProvider` 呼叫 `AsyncFileEditorProvider.createEditorAsync`）。

保留原因（逐項，非本輪引入的新債）：
- `StartupUiUtil.isDarkTheme()`（internal，3 source sites：`LCVPanel.createHtml`/`getStyle`/`updateStyle`）——它正是官方 deprecated API 上 `@ReplaceWith` 指定的替代目標本身即為 internal，沒有更外層的公開包裝，只能繼續用。
- `ConvergeProvider.getBuilderFromEditorProvider` 呼叫 `AsyncFileEditorProvider.createEditorAsync`（override-only，1 site）——官方文件範例本身就是這種「委派呼叫底層 provider 的 override-only 方法」用法，非誤用。
- `SplitFileEditor.createToolbarFromGroupId` 對 `ActionToolbarImpl` 的 cast（internal，1 site）——公開 API 沒有暴露等價的 toolbar 建構入口。
- `ErrorReportHandler.submit` 對 `AbstractMessage`（internal，1 site／2 records）——拿掉會犧牲例外堆疊的精確度（改走公開的粗粒度例外 API 會遺失原始 cause）。
- `FileTypeChooser.getKnownFileTypeOrAssociate`（SFR）／`EditorColors.SCROLLBAR_THUMB_COLOR`（SFR）／`TextEditorWithPreview.MyFileEditorState`（deprecated）——本輪未驗證出可信的替代 API，留待下輪個別查證。
- `PluginManagerCore.getPlugin(PluginId)` ×3 source sites（`UpdateUtils`、`SentryUtils`、`RegisterPluginInstallerStateListener`）——曾規劃換成 `PluginManager.getInstance().findEnabledPlugin(PluginId)`，javap 反編譯 2026.2 平台 jar（`intellij.platform.core.impl.jar`）確認該替代方法本身也是 `@ApiStatus.Internal`（雖非 deprecated）——換了不會減少 internal 筆數、也不會少一次「262 無受支援的 descriptor 查詢替代」的殘留，故維持原樣不動。

## 建置地雷
- `buildPlugin` 內含 `buildSearchableOptions`，需**獨佔一個 IDE 實例**；殘留報 `Only one instance of IDEA can be run at a time`（環境問題非程式碼）：`pkill -9 -f ideaIU` 後重跑（`scripts/release.sh` 已內建）。

## 其他
- `leetcode.cn` 匿名被 Cloudflare 擋（curl/單元測試打不通），只有 JCEF 登入後可用；`leetcode.com` GraphQL 正常。
- 發版：`scripts/release.sh [--verify] [--push]`，別手組 shell。
