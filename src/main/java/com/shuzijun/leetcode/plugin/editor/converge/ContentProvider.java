package com.shuzijun.leetcode.plugin.editor.converge;

import com.intellij.openapi.application.ApplicationManager;
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
            // 語意與改動前完全相同。
            try {
                contentVf = ApplicationManager.getApplication().executeOnPooledThread(() -> LocalFileSystem.getInstance().refreshAndFindFileByNioFile(contentFile)).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return super.createEditor(project, contentVf);
    }
}
