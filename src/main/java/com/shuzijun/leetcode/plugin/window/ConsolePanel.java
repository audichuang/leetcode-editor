package com.shuzijun.leetcode.plugin.window;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import com.shuzijun.leetcode.plugin.utils.DataKeys;
import org.jetbrains.annotations.NotNull;

/**
 * @author shuzijun
 */
public class ConsolePanel extends SimpleToolWindowPanel {

    private ConsoleView consoleView;

    public ConsolePanel(ToolWindow toolWindow, Project project) {
        super(Boolean.FALSE, Boolean.TRUE);
        this.consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        setContent(consoleView.getComponent());
        final DefaultActionGroup consoleGroup = new DefaultActionGroup(consoleView.createConsoleActions());
        ActionToolbar consoleToolbar = ActionManager.getInstance().createActionToolbar(PluginConstant.ACTION_PREFIX + " ConsoleToolbar", consoleGroup, true);
        consoleToolbar.setTargetComponent(consoleView.getComponent());
        setToolbar(consoleToolbar.getComponent());

    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);
        sink.set(DataKeys.LEETCODE_CONSOLE_VIEW, consoleView);
    }

    public ConsoleView getConsoleView() {
        return consoleView;
    }
}
