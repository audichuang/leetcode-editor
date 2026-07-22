package com.shuzijun.leetcode.plugin.editor.converge;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.shuzijun.leetcode.plugin.editor.LCVProvider;
import com.shuzijun.leetcode.plugin.model.LeetcodeEditor;
import com.shuzijun.leetcode.plugin.setting.ProjectConfig;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author shuzijun
 */
public class ContentProvider extends LCVProvider {

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return true;
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
            // 平台允許同步呼叫。原本包一層 executeOnPooledThread(...).get() 並不會避開阻塞:
            // 呼叫緒仍卡在 .get() 等 pooled thread 做完 VFS refresh,而 refresh 派發的事件可能
            // 又回派 EDT 處理,若呼叫緒本身就是 EDT 就是死鎖窗口,反而比直接同步呼叫更危險。
            // 不改走 AsyncFileEditorProvider:ConvergeProvider 已控 async 建構,為了這條罕見
            // fallback 動開題熱路徑不成比例。
            contentVf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(contentFile);
        }
        if (contentVf == null) {
            // 262 起 refreshAndFindFileByNioFile 回傳 @Nullable(檔案被刪或無法 refresh 時)。
            // 下游 super.createEditor(project, contentVf) 對 VirtualFile 是 non-null 契約,
            // 靜默餵 null 會在稍後 editor 建立時以難懂的方式失敗——這裡先帶路徑 fail-fast。
            throw new IllegalStateException("LeetCode content file missing or not refreshable: " + contentFile);
        }
        return super.createEditor(project, contentVf);
    }
}
