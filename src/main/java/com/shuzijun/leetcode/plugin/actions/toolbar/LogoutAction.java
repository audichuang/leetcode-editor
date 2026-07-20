package com.shuzijun.leetcode.plugin.actions.toolbar;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.shuzijun.leetcode.plugin.actions.AbstractAction;
import com.shuzijun.leetcode.plugin.listener.LoginNotifier;
import com.shuzijun.leetcode.plugin.model.Config;
import com.shuzijun.leetcode.plugin.model.HttpRequest;
import com.shuzijun.leetcode.plugin.setting.PersistentConfig;
import com.shuzijun.leetcode.plugin.utils.*;
import com.shuzijun.leetcode.plugin.window.NavigatorTabsPanel;

/**
 * @author shuzijun
 */
public class LogoutAction extends AbstractAction implements DumbAware {
    @Override
    public void actionPerformed(AnActionEvent anActionEvent, Config config) {

        HttpResponse httpResponse = HttpRequest.builderGet(URLUtils.getLeetcodeLogout()).request();
        // 先清磁碟 cookie、再清記憶體：否則兩步之間若有並發 getUser 觸發 restore，會看到「記憶體已空、磁碟還在」而把已登出帳號載回。
        // 反序後：清記憶體前 restore 看到 cookie 仍在會短路、清記憶體後看到磁碟已空不載回，任何時點都安全（見 HttpLogin.restorePersistedCookies）。
        config.addCookie(config.getUrl() + config.getLoginName(), null);
        PersistentConfig.getInstance().setInitConfig(config);
        HttpRequestUtils.resetHttpclient();
        MessageUtils.getInstance(anActionEvent.getProject()).showInfoMsg("info", PropertiesUtils.getInfo("login.out"));
        NavigatorTabsPanel.loadUser(false);
        ApplicationManager.getApplication().getMessageBus().syncPublisher(LoginNotifier.TOPIC).logout(anActionEvent.getProject(), config.getUrl());
    }
}
