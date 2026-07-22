package com.shuzijun.leetcode.plugin.window;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.shuzijun.leetcode.plugin.manager.NavigatorAction;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import com.shuzijun.leetcode.plugin.model.User;
import com.shuzijun.leetcode.plugin.setting.PersistentConfig;
import icons.LeetCodeEditorIcons;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author shuzijun
 */
public class WindowFactory implements ToolWindowFactory, DumbAware {

    public static String ID = PluginConstant.TOOL_WINDOW_ID;

    // Project-scoped registry for BGT manager-layer callers (QuestionManager/FindManager/
    // SessionManager/HttpLogin, all off executeOnPooledThread) and cross-context actions
    // (e.g. PositionAction, registered on both the navigator toolbar and the editor popup
    // menu) that cannot rely on AnActionEvent#getData()'s DataContext snapshot. Plain
    // UserData get/put is an O(1), concurrent-safe map lookup — no ToolWindowManager/
    // ContentManager/Swing traversal, so it never asserts EDT and naturally avoids the
    // dispose race (NavigatorTabsPanel#dispose() clears the entry, so a lookup after
    // disposal returns null instead of touching a stale panel).
    public static final Key<NavigatorTabsPanel> NAVIGATOR_PANEL_KEY = Key.create("leetcode.navigatorTabsPanel");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        ContentFactory contentFactory = ContentFactory.getInstance();
        NavigatorTabsPanel navigatorPanel = new NavigatorTabsPanel(toolWindow, project);
        project.putUserData(NAVIGATOR_PANEL_KEY, navigatorPanel);
        Content content = contentFactory.createContent(navigatorPanel, "", false);
        content.setDisposer(navigatorPanel);
        toolWindow.getContentManager().addContent(content);
        if (PersistentConfig.getInstance().getInitConfig() != null) {
            if (!PersistentConfig.getInstance().getInitConfig().getShowToolIcon()) {
                toolWindow.setIcon(LeetCodeEditorIcons.EMPEROR_NEW_CLOTHES);
            }
            if (!PersistentConfig.getInstance().getInitConfig().isLeftQuestionEditor()) {
                toolWindow.setAnchor(ToolWindowAnchor.RIGHT, null);
            }

        }
    }

    @Nullable
    public static NavigatorTabsPanel getNavigatorTabsPanel(@NotNull Project project) {
        return project.getUserData(NAVIGATOR_PANEL_KEY);
    }

    // Lightweight direct lookup for BGT manager-layer callers and cross-context actions.
    // Deliberately bypasses DataContext/DataProvider/uiDataSnapshot. Returns null — never a
    // fabricated anonymous User — when the panel isn't up yet (project still opening/closing):
    // a fake guest User would pollute request params and HTTP cache partitioning as if it were
    // a real anonymous session. Callers must treat null as "panel unavailable", not "signed out".
    @Nullable
    public static User getUser(@NotNull Project project) {
        NavigatorTabsPanel panel = getNavigatorTabsPanel(project);
        return panel == null ? null : panel.getUser();
    }

    @Nullable
    public static NavigatorAction getNavigatorAction(@NotNull Project project) {
        NavigatorTabsPanel panel = getNavigatorTabsPanel(project);
        return panel == null ? null : panel.getCurrentNavigatorAction();
    }

    public static void updateTitle(@NotNull Project project, String userName) {
        ToolWindow leetcodeToolWindows = ToolWindowManager.getInstance(project).getToolWindow(ID);
        ApplicationManager.getApplication().invokeLater(() -> {
            if (StringUtils.isNotBlank(userName)) {
                leetcodeToolWindows.setTitle("[" + userName + "]");
            } else {
                leetcodeToolWindows.setTitle("");
            }
        });

    }

    public static void activateToolWindow(@NotNull Project project) {
        ToolWindow leetcodeToolWindows = ToolWindowManager.getInstance(project).getToolWindow(ID);
        leetcodeToolWindows.activate(null);
    }

}
