package com.shuzijun.leetcode.plugin.actions.tree;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.shuzijun.leetcode.plugin.manager.FavoriteManager;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import com.shuzijun.leetcode.plugin.model.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author shuzijun
 */
public class FavoriteAction extends ToggleAction implements DumbAware {

    private Tag tag;

    private String questionId;

    private String titleSlug;

    public FavoriteAction(@Nullable String text, Tag tag, String questionId, String titleSlug) {
        super(text);
        this.tag = tag;
        this.questionId = questionId;
        this.titleSlug = titleSlug;
    }

    @Override
    public boolean isSelected(AnActionEvent anActionEvent) {
        return questionId != null && tag.getQuestions().contains(questionId);
    }

    @Override
    public void setSelected(AnActionEvent anActionEvent, boolean b) {
        if (titleSlug == null) {
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(anActionEvent.getProject(), PluginConstant.PLUGIN_NAME + ".favorite", false) {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                if (b) {
                    FavoriteManager.addQuestionToFavorite(tag, titleSlug, anActionEvent.getProject());
                } else {
                    FavoriteManager.removeQuestionFromFavorite(tag, titleSlug, anActionEvent.getProject());
                }
            }
        });

    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return  ActionUpdateThread.BGT;
    }
}
