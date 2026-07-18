#!/usr/bin/env bash
# 一鍵 build → 驗證 → (可選) Plugin Verifier → 發布/更新 GitHub Release。
# 把散落在對話裡、每次重組的 shell 固化成單一事實來源，省 token、少出錯。
#
# 用法:
#   scripts/release.sh                 # build + 驗證 zip 內容（不碰 GitHub）
#   scripts/release.sh --verify        # 額外跑 verifyPlugin（下載目標 IDE，慢）
#   scripts/release.sh --push          # 額外把 zip 發布/更新到 GitHub Release
#   scripts/release.sh --verify --push # 全套
#
# 建議在背景執行並等通知（省得中途一直 poll）:
#   nohup scripts/release.sh --push > /tmp/release.log 2>&1 &
set -uo pipefail

cd "$(cd "$(dirname "$0")/.." && pwd)"

FORK_REMOTE="${FORK_REMOTE:-fork}"
GH_REPO="${GH_REPO:-audichuang/leetcode-editor}"
DO_VERIFY=0; DO_PUSH=0
for a in "$@"; do
  case "$a" in
    --verify) DO_VERIFY=1;;
    --push)   DO_PUSH=1;;
    *) echo "unknown arg: $a"; exit 2;;
  esac
done

# 0. 清殘留 IDE：buildSearchableOptions 需獨佔一個 IDE 實例，殘留會報 "Only one instance of IDEA"
pkill -9 -f "ideaIU-20" 2>/dev/null || true
sleep 2

# 1. build + test
echo "==> ./gradlew test buildPlugin"
./gradlew test buildPlugin --console=plain || { echo "❌ build/test 失敗"; exit 1; }

# 2. 定位 zip + 版本
ZIP=$(ls -t build/distributions/leetcode-editor-*.zip 2>/dev/null | head -1)
[ -n "$ZIP" ] || { echo "❌ 找不到 build/distributions 的 zip"; exit 1; }
VERSION=$(basename "$ZIP" | sed -E 's/^leetcode-editor-(.*)\.zip$/\1/')
echo "==> built: $ZIP  (v$VERSION)"

# 3. 驗證 zip 內 plugin.xml —— 正確地進 lib/*.jar 內查（plugin.xml 不在 zip 根 META-INF）
TMP=$(mktemp -d)
unzip -oq "$ZIP" -d "$TMP"
# 排除 searchableOptions 側車 jar（它沒有 plugin.xml，find 順序不定時會被 head -1 抓到）
MAINJAR=$(find "$TMP" -name "leetcode-editor*.jar" ! -name "*searchableOptions*" -path "*/lib/*" | head -1)
[ -n "$MAINJAR" ] || { echo "❌ zip 內找不到 main jar"; rm -rf "$TMP"; exit 1; }
PLUGINXML=$(unzip -p "$MAINJAR" META-INF/plugin.xml)
rm -rf "$TMP"
# 硬性 invariant：用了 JCEF 就必須宣告依賴，否則 2026.x 一裝就啟動崩潰
echo "$PLUGINXML" | grep -q "com.intellij.modules.jcef" \
  || { echo "❌ plugin.xml 缺 <depends>com.intellij.modules.jcef</depends>（2026.x 會啟動崩潰）"; exit 1; }
echo "==> plugin.xml OK: $(echo "$PLUGINXML" | grep -oE 'since-build="[^"]*"') + jcef depends present"

# 4. 可選：Plugin Verifier（唯一能靜態抓「缺 module 依賴」這類啟動崩潰的工具）
if [ "$DO_VERIFY" = 1 ]; then
  echo "==> ./gradlew verifyPlugin (下載目標 IDE，慢)"
  ./gradlew verifyPlugin --console=plain || { echo "❌ verifyPlugin 發現相容性問題，見上方報告"; exit 1; }
  echo "==> verifyPlugin 通過"
fi

# 5. 可選：發布/更新 GitHub Release（同版本號則更新 asset，不重建）
if [ "$DO_PUSH" = 1 ]; then
  if gh release view "v$VERSION" --repo "$GH_REPO" >/dev/null 2>&1; then
    echo "==> 更新既有 release v$VERSION 的 zip"
    gh release upload "v$VERSION" "$ZIP" --repo "$GH_REPO" --clobber || { echo "❌ 上傳失敗"; exit 1; }
  else
    echo "==> 建立 release v$VERSION"
    gh release create "v$VERSION" --repo "$GH_REPO" --latest --title "v$VERSION" \
      --notes "leetcode-editor v$VERSION — see commits for details." "$ZIP" || { echo "❌ 建立失敗"; exit 1; }
  fi
  echo "==> release: $(gh release view "v$VERSION" --repo "$GH_REPO" --json url --jq .url)"
fi

echo "✅ done (v$VERSION)"
