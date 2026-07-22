package com.shuzijun.leetcode.plugin.actions.tree;

import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.shuzijun.leetcode.plugin.manager.NavigatorAction;
import com.shuzijun.leetcode.plugin.manager.QuestionManager;
import com.shuzijun.leetcode.plugin.model.Constant;
import com.shuzijun.leetcode.plugin.model.Question;
import com.shuzijun.leetcode.plugin.model.QuestionView;
import com.shuzijun.leetcode.plugin.model.Tag;
import com.shuzijun.leetcode.plugin.utils.DataKeys;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author shuzijun
 */
public class FavoriteActionGroup extends ActionGroup implements DumbAware {

    @Override
    public AnAction[] getChildren(AnActionEvent anActionEvent) {
        // BGT-safe: read via the event's (async, EDT-snapshotted) DataContext instead of
        // walking ToolWindowManager/ContentManager/Swing directly (WindowFactory's old
        // direct-lookup helper, since removed), which asserted EDT. Same fix as
        // FindActionGroup. The selected row's QuestionView additionally requires a JTable
        // read (getSelectedRowData()), which must not happen on BGT either —
        // NavigatorTabsPanel#uiDataSnapshot() resolves it on EDT during the platform's
        // snapshot; see LEETCODE_PROJECTS_SELECTED_QUESTION.
        if (anActionEvent == null) {
            return AnAction.EMPTY_ARRAY;
        }
        NavigatorAction navigatorAction = anActionEvent.getData(DataKeys.LEETCODE_PROJECTS_NAVIGATORACTION);
        if (navigatorAction == null) {
            return AnAction.EMPTY_ARRAY;
        }
        QuestionView questionView = anActionEvent.getData(DataKeys.LEETCODE_PROJECTS_SELECTED_QUESTION);
        String questionId = null;
        String titleSlug = null;
        if (questionView != null) {
            titleSlug = questionView.getTitleSlug();
            Question question = QuestionManager.getQuestionByTitleSlug(titleSlug, anActionEvent.getProject(), true);
            if (question != null) {
                questionId = question.getQuestionId();
            }
        }

        List<AnAction> anActionList = Lists.newArrayList();
        List<Tag> tags = navigatorAction.getFind().getFilter(Constant.FIND_TYPE_LISTS);
        if (tags != null && !tags.isEmpty()) {
            for (Tag tag : tags) {
                if (!"leetcode_favorites".equals(tag.getType())) {
                    anActionList.add(new FavoriteAction(tag.getName(), tag, questionId, titleSlug));
                }
            }
        }
        AnAction[] anActions = new AnAction[anActionList.size()];
        anActionList.toArray(anActions);
        return anActions;

    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return  ActionUpdateThread.BGT;
    }

}
