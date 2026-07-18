---
name: release-plugin
description: Build、驗證並發布 leetcode-editor 外掛的新版本。當使用者要「發新版本、重新打包、更新 GitHub Release 的 zip、跑 Plugin Verifier 確認相容性、給使用者重新下載」時使用。封裝 scripts/release.sh，避免每次手動重組 pkill/gradlew/unzip/gh 這串 shell，省 token。
---

# 發布 leetcode-editor 新版本

**不要**每次手動組 build / unzip 檢查 / gh release 指令。一律呼叫已固化的腳本：

```bash
scripts/release.sh [--verify] [--push]
```

- 無旗標：`pkill 殘留 IDE → gradlew test buildPlugin → 驗證 zip 內 plugin.xml（含 jcef 依賴、since-build）`。日常改完 code 就跑這個確認沒壞。
- `--verify`：額外跑 `verifyPlugin`（Plugin Verifier against build.gradle.kts 配置的目標，含 2026.2）。**這是唯一能靜態抓「用了平台 class 卻沒宣告 module 依賴」這類啟動崩潰的工具**——動 JCEF/平台 API、或要給使用者裝之前，跑這個。
- `--push`：把 zip 發布或更新到 GitHub Release（同版本號則 `--clobber` 更新 asset，不重建）。

## 執行方式（省 token）

背景執行 + 等通知，不要中途一直 poll：

```bash
# 用 Bash 工具的 run_in_background:true 跑，完成會通知
scripts/release.sh --verify --push > /tmp/release.log 2>&1
```

跑完只看結尾（`✅ done` 或 `❌ ...`）與 release URL，不要逐行讀整份 gradle log。

## 為什麼存在（踩過的雷）

- `plugin.xml` 在 `lib/*.jar` **內**，不在 zip 根 `META-INF`——直接 `find` 解壓目錄會誤判「沒打包」。腳本已正確進 jar 查。
- 版本鎖與相容性 invariant 見 repo 根 `AGENTS.md`。改版本前先讀。
