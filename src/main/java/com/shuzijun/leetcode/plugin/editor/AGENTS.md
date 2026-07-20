# editor — 題目編輯器與 JCEF 預覽

> 共通見 repo 根 `AGENTS.md`，本檔只寫本模組增量。這是全專案最複雜、踩雷最多的一區。

自訂 `FileEditor`（左邊寫碼、右邊預覽）+ JCEF 瀏覽器渲染題目/題解，靠一個內嵌 HTTP server 餵靜態資源給 JCEF。

## 業務地圖
- `LCVPanel` — JCEF 瀏覽器（vditor 渲染題目/筆記 markdown）。**最複雜、先看它**。
- `PreviewStaticServer` + `BaseController` + `ResourcesController` — 內嵌 HTTP server，把 vditor 的 JS/CSS 餵給 JCEF（JCEF 不能直接讀 jar 內資源）。
- `ProxyLoadHtmlResourceHandler` — 代理 JCEF 抓不到的 iframe/遠端圖片資源。
- `SplitFileEditor` / `QuestionEditorWithPreview` / `*Provider` — split editor 框架與註冊。
- `converge/` — 右側三個預覽 tab：`SolutionPreview`(題解) / `SubmissionsPreview`(提交) / `NotePreview`(筆記)，各配一個 `*Provider`。
- `LCVFileType` / `LCVLanguage` — `.lcv` 檔類型。

## 進來改要遵守
- `converge/*Preview` 的資料抓取**一律在背景緒**，EDT 只組 UI，且回呼要檢查 `project.isDisposed() || disposed || myGen != generation`（防面板關閉後/亂序覆蓋）。別退回同步 `.get()`。
- `LCVFileType.getCharset()` 固定回 UTF-8（上游 #373 亂碼根因），別移除。
- `LCVPanel` 遠端圖片只在有 IDE proxy 時才走中轉（#553），無 proxy 維持 JCEF 直連——別改成一律中轉。
- 內容/題解預覽用**靜態 `Vditor.preview`**（`default.html` 載 `method.min.js`），不是完整 `new Vditor` 編輯器（省 SV editor 建構 + preview 固定 1s debounce）。別退回完整 editor；template 裡手動補的 `.vditor`/`.vditor--dark` class、`lang`、置中 padding 是修「靜態模式相對完整 editor」的語系/版面回歸，別當多餘刪；主題切換走 static `Vditor.setContentTheme`/`setCodeTheme`（無 instance）。

## 踩雷
- **每開一題就 new 一個 JCEF 瀏覽器實例**（單一實例吃數十~上百 MB）。別「優化」成單例/池化——那會犧牲多題同時預覽的功能。
- `LCVPanel.iframe` 用 `Set` 且 `reloadText` 時 `clear()`（否則洩漏 + O(n²)）。

細節查 codegraph。
