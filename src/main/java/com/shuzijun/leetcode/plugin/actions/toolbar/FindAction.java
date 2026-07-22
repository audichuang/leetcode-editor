package com.shuzijun.leetcode.plugin.actions.toolbar;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.shuzijun.leetcode.plugin.manager.NavigatorAction;
import com.shuzijun.leetcode.plugin.utils.DataKeys;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author shuzijun
 */
public class FindAction extends ToggleAction implements DumbAware {

    private int i = 0;

    @Override
    public boolean isSelected(AnActionEvent anActionEvent) {
        if (anActionEvent.getProject() == null) {
            //Why is it null?
            return false;
        }
        NavigatorAction navigatorAction = anActionEvent.getData(DataKeys.LEETCODE_PROJECTS_NAVIGATORACTION);
        if (navigatorAction == null) {
            return false;
        }
        JPanel panel = navigatorAction.queryPanel();
        if (panel == null) {
            return false;
        }
        return panel.isVisible();
    }

    @Override
    public void setSelected(AnActionEvent anActionEvent, boolean b) {
        NavigatorAction navigatorAction = anActionEvent.getData(DataKeys.LEETCODE_PROJECTS_NAVIGATORACTION);
        if (navigatorAction == null) {
            return;
        }
        JPanel panel = navigatorAction.queryPanel();
        if (panel == null) {
            return;
        }
        panel.setVisible(b);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // EDT required: isSelected() reads Swing's panel.isVisible(), which is not
        // safe to call off the EDT. This action does not read NavigatorAction via
        // WindowFactory's old (since-removed) direct-lookup helper either way — it uses
        // AnActionEvent#getData() (see above) — but the Swing read is the reason this
        // must stay/become EDT.
        return ActionUpdateThread.EDT;
    }
}
