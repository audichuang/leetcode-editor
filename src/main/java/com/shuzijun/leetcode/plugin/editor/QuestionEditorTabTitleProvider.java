package com.shuzijun.leetcode.plugin.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.shuzijun.leetcode.plugin.manager.QuestionManager;
import com.shuzijun.leetcode.plugin.model.Config;
import com.shuzijun.leetcode.plugin.model.LeetcodeEditor;
import com.shuzijun.leetcode.plugin.model.Question;
import com.shuzijun.leetcode.plugin.setting.PersistentConfig;
import com.shuzijun.leetcode.plugin.setting.ProjectConfig;
import com.shuzijun.leetcode.plugin.utils.LogUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author shuzijun
 */
public class QuestionEditorTabTitleProvider implements EditorTabTitleProvider {

    // cache miss 時背景抓題進行中的 project+file path，防同一 project 內同一檔重複觸發抓取；抓取完成（成功或失敗）就移除
    private static final Set<String> refreshing = ConcurrentHashMap.newKeySet();

    @Override
    public @NlsContexts.TabTitle @Nullable String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
        try {
            Config config = PersistentConfig.getInstance().getInitConfig();
            if (config == null || !config.isShowQuestionEditor() || !config.isShowQuestionEditorSign()) {
                return null;
            }
            LeetcodeEditor leetcodeEditor = ProjectConfig.getInstance(project).getEditor(file.getPath(), config.getUrl());
            if (leetcodeEditor == null || StringUtils.isBlank(leetcodeEditor.getContentPath())) {
                return null;
            } else {
                // cache-only：避免在 EDT 同步發網路請求卡住 UI
                Question question = QuestionManager.getQuestionByTitleSlug(leetcodeEditor.getTitleSlug(), project, true);
                if (question == null) {
                    scheduleRefresh(project, file, leetcodeEditor.getTitleSlug());
                    return null;
                } else {
                    return question.getFormTitle();
                }
            }
        } catch (Throwable e) {
            LogUtils.LOG.error("QuestionEditorIconProvider -> patchIcon", e);
            return null;
        }
    }

    // cache miss 時背景抓一次題目填快取，成功後請平台重新問一次標題；同一檔已有抓取在跑就不重複觸發
    private void scheduleRefresh(Project project, VirtualFile file, String titleSlug) {
        // key 帶 project 身分：多 project 冷啟同時開同一實體檔時，各 project 都要自己抓一次並刷新自己的 presentation
        // （QuestionManager 快取是全域的，第二次抓會命中快取，成本近零）
        // 用 basePath 而非 getLocationHash()：後者是 32-bit hash，存在真實碰撞（如 "/tmp/Aa" vs "/tmp/BB"）
        String key = project.getBasePath() + ":" + file.getPath();
        if (!refreshing.add(key)) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Question question = QuestionManager.getQuestionByTitleSlug(titleSlug, project);
                if (question == null) {
                    return;
                }
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (project.isDisposed() || !file.isValid()) {
                        return;
                    }
                    FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file);
                });
            } finally {
                refreshing.remove(key);
            }
        });
    }
}
