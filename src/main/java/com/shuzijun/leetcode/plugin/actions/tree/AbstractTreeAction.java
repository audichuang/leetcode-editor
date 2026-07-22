package com.shuzijun.leetcode.plugin.actions.tree;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.shuzijun.leetcode.plugin.actions.AbstractAction;
import com.shuzijun.leetcode.plugin.manager.QuestionManager;
import com.shuzijun.leetcode.plugin.model.Config;
import com.shuzijun.leetcode.plugin.model.Question;
import com.shuzijun.leetcode.plugin.model.QuestionView;
import com.shuzijun.leetcode.plugin.utils.DataKeys;

/**
 * @author shuzijun
 */
public abstract class AbstractTreeAction extends AbstractAction implements DumbAware {

    @Override
    public void actionPerformed(AnActionEvent anActionEvent, Config config) {
        // Reads the LEETCODE_PROJECTS_SELECTED_QUESTION snapshot key instead of the live
        // NavigatorAction: this runs inside a Task.Backgroundable (AbstractAction.java:44), a
        // background thread, and NavigatorAction#getSelectedRowData() would call
        // JTable.getSelectedRow() off-EDT. NavigatorTabsPanel#uiDataSnapshot() already resolved
        // this key on EDT while the platform snapshotted the event's DataContext.
        QuestionView questionView = anActionEvent.getData(DataKeys.LEETCODE_PROJECTS_SELECTED_QUESTION);
        if (questionView == null) {
            return;
        }
        Question question = QuestionManager.getQuestionByTitleSlug(questionView.getTitleSlug(), anActionEvent.getProject());
        if (question == null) {
            return;
        }
        actionPerformed(anActionEvent, config, question);
    }

    public abstract void actionPerformed(AnActionEvent anActionEvent, Config config, Question question);
}
