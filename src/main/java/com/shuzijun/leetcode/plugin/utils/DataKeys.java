package com.shuzijun.leetcode.plugin.utils;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.DataKey;
import com.shuzijun.leetcode.plugin.manager.NavigatorAction;
import com.shuzijun.leetcode.plugin.model.QuestionView;
import com.shuzijun.leetcode.plugin.window.NavigatorTabsPanel;

/**
 * @author shuzijun
 */
public class DataKeys {

    public static final DataKey<ConsoleView> LEETCODE_CONSOLE_VIEW = DataKey.create("LEETCODE_CONSOLE_VIEW");

    public static final DataKey<NavigatorTabsPanel> LEETCODE_PROJECTS_TABS = DataKey.create("LEETCODE_PROJECTS_TABS");

    public static final DataKey<NavigatorAction> LEETCODE_PROJECTS_NAVIGATORACTION = DataKey.create("LEETCODE_PROJECTS_NAVIGATORACTION");

    // Selected row's QuestionView, read off the current navigator tab's JTable. Only ever
    // populated by NavigatorTabsPanel#getData() on EDT (platform's PreCachedDataContext
    // snapshot) — never read Swing selection directly from a BGT action/group.
    public static final DataKey<QuestionView> LEETCODE_PROJECTS_SELECTED_QUESTION = DataKey.create("LEETCODE_PROJECTS_SELECTED_QUESTION");
}
