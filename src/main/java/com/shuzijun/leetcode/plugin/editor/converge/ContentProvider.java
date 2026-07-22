package com.shuzijun.leetcode.plugin.editor.converge;

import com.intellij.openapi.fileEditor.AsyncFileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.shuzijun.leetcode.plugin.editor.LCVProvider;
import com.shuzijun.leetcode.plugin.model.LeetcodeEditor;
import com.shuzijun.leetcode.plugin.setting.ProjectConfig;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * @author shuzijun
 */
public class ContentProvider extends LCVProvider implements AsyncFileEditorProvider {

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return true;
    }

    @Override
    public @NotNull Builder createEditorAsync(@NotNull Project project, @NotNull VirtualFile file) {
        // 262 的 async 開檔 bridge 是 readAction { createEditorAsync(...) } → Dispatchers.EDT
        // { build() }(AsyncFileEditorProvider.createFileEditor$suspendImpl bytecode 驗證)。
        // 所以這裡(prep 階段、BGT、read action 內)只能做純查找:同步 VFS refresh 要等 EDT
        // 以 write action 套事件,在 read action 裡呼叫就是死鎖,絕不能搬進來。
        LeetcodeEditor leetcodeEditor = ProjectConfig.getInstance(project).getEditor(file.getPath());
        Path contentFile = Path.of(leetcodeEditor.getContentPath());
        VirtualFile prepared = LocalFileSystem.getInstance().findFileByNioFile(contentFile);
        if (prepared == null) {
            // VFS 快照遺失 content 檔:這裡不能同步 refresh(read action 內),改排非阻塞
            // async refresh 讓 refresh worker 先跑;到 build() 時多半已補進 VFS,可免掉
            // EDT 上的同步 refresh 最後手段。
            LocalFileSystem.getInstance().refreshNioFiles(List.of(contentFile), true, false, null);
        }
        return new Builder() {
            @Override
            public FileEditor build() {
                // prep 命中且仍有效 → EDT 零 VFS 工作;prep miss 或期間被刪/替換
                // (isValid=false)→ 再做一次純查找(上面的 async refresh 或替換產生的
                // VFS 事件多半已補上新節點,查找不碰磁碟)。
                VirtualFile contentVf = (prepared != null && prepared.isValid())
                        ? prepared
                        : LocalFileSystem.getInstance().findFileByNioFile(contentFile);
                if (contentVf != null && contentVf.isValid()) {
                    return buildForContent(project, contentVf);
                }
                // 最後手段:EDT、無 read action,同步 refresh 合法(慢碟會卡 UI,但
                // legacy Builder API 在 miss 時沒有可暫停的 BGT 階段;不能上移的理由
                // 見 createEditorAsync 開頭)。走 createEditor 的完整 fallback 含 fail-fast。
                return createEditor(project, file);
            }
        };
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        LeetcodeEditor leetcodeEditor = ProjectConfig.getInstance(project).getEditor(file.getPath());
        Path contentFile = Path.of(leetcodeEditor.getContentPath());

        // 熱路徑:CodeManager.openContent -> FileUtils.openFileEditorAndSaveState:239 早已在 BGT
        // 對 content 檔跑過 refreshAndFindFileByIoFile,VFS 樹此時必已認識該檔案。
        // 純查找不碰磁碟,EDT 安全,不必再開 pooled thread 同步等 refresh。
        VirtualFile contentVf = LocalFileSystem.getInstance().findFileByNioFile(contentFile);
        if (contentVf == null) {
            // fallback:罕見情況(例如重開 IDE 還原 editor tab 時 VFS 持久快照遺失該檔)。
            // 直接在呼叫緒同步 refresh——262 的 refreshAndFindFileByNioFile 未標 background-only,
            // 平台允許同步呼叫(前提:不能持有 read action;async 路徑由 createEditorAsync 的
            // prep 純查找承擔,只有查不到才會落到這裡的 EDT/同步呼叫端)。原本包一層
            // executeOnPooledThread(...).get() 並不會避開阻塞:呼叫緒仍卡在 .get() 等 pooled
            // thread 做完 VFS refresh,而 refresh 派發的事件可能又回派 EDT 處理,若呼叫緒本身
            // 就是 EDT 就是死鎖窗口,反而比直接同步呼叫更危險。
            contentVf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(contentFile);
        }
        if (contentVf == null) {
            // 262 起 refreshAndFindFileByNioFile 回傳 @Nullable(檔案被刪或無法 refresh 時)。
            // 下游 super.createEditor(project, contentVf) 對 VirtualFile 是 non-null 契約,
            // 靜默餵 null 會在稍後 editor 建立時以難懂的方式失敗——這裡先帶路徑 fail-fast。
            throw new IllegalStateException("LeetCode content file missing or not refreshable: " + contentFile);
        }
        return buildForContent(project, contentVf);
    }

    /** 對「已解析好的 content 檔」建 editor:走 LCVProvider 的建構,跳過本類的 content 解析。 */
    private FileEditor buildForContent(@NotNull Project project, @NotNull VirtualFile contentVf) {
        return super.createEditor(project, contentVf);
    }
}
