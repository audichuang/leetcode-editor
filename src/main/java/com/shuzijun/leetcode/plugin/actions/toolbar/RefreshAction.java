package com.shuzijun.leetcode.plugin.actions.toolbar;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.shuzijun.leetcode.plugin.actions.AbstractAction;
import com.shuzijun.leetcode.plugin.manager.NavigatorAction;
import com.shuzijun.leetcode.plugin.manager.QuestionManager;
import com.shuzijun.leetcode.plugin.model.Config;
import com.shuzijun.leetcode.plugin.utils.DataKeys;
import com.shuzijun.leetcode.plugin.utils.HttpRequestUtils;
import com.shuzijun.leetcode.plugin.window.WindowFactory;

/**
 * @author shuzijun
 */
public class RefreshAction extends AbstractAction implements DumbAware {
    @Override
    public void actionPerformed(AnActionEvent anActionEvent, Config config) {

        HttpRequestUtils.invalidateCache();
        // Refresh 應連 2 天 TTL 的全量題庫(questionAllCache)一起作廢，否則 All 分頁會繼續顯示舊狀態達兩天
        QuestionManager.invalidateAll();
        NavigatorAction navigatorAction = WindowFactory.getDataContext(anActionEvent.getProject()).getData(DataKeys.LEETCODE_PROJECTS_NAVIGATORACTION);
        navigatorAction.getFind().operationType("");
        navigatorAction.findClear();
    }


}
