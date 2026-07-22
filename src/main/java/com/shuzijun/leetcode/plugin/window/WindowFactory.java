package com.shuzijun.leetcode.plugin.window;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
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
    // dispose race (a LIFO child Disposable registered in createToolWindowContent clears
    // the entry before any child panel teardown starts, so a lookup during/after disposal
    // returns null instead of touching a dying panel).
    public static final Key<NavigatorTabsPanel> NAVIGATOR_PANEL_KEY = Key.create("leetcode.navigatorTabsPanel");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        ContentFactory contentFactory = ContentFactory.getInstance();
        NavigatorTabsPanel navigatorPanel = new NavigatorTabsPanel(toolWindow, project);
        project.putUserData(NAVIGATOR_PANEL_KEY, navigatorPanel);
        // compare-and-clear 註冊成 panel 的子 Disposable:Disposer 以 LIFO 銷毀子節點,
        // 這個 clear 在建構子註冊的三個子面板之後註冊 → disposal 時最先跑,任何子面板/
        // 表格開始拆除前 registry 就已回 null,關掉「BGT 在 disposal 窗口期拿到垂死
        // panel」的窗口(262 的 Disposer.isDisposed 已 deprecated,不能靠它在 getter 把關)。
        // compare 條件防的是:平台先建好新 panel 才 dispose 舊 panel 時,舊 clear 抹掉新註冊。
        Disposer.register(navigatorPanel, () -> {
            if (project.getUserData(NAVIGATOR_PANEL_KEY) == navigatorPanel) {
                project.putUserData(NAVIGATOR_PANEL_KEY, null);
            }
        });
        Content content = contentFactory.createContent(navigatorPanel, "", false);
        content.setDisposer(navigatorPanel);
        toolWindow.getContentManager().addContent(content);
        // registry 註冊完、content 掛好才啟動背景初始化/訂閱(見 NavigatorTabsPanel.start javadoc)。
        navigatorPanel.start();
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
