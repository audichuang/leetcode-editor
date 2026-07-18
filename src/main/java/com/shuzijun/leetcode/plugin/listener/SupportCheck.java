package com.shuzijun.leetcode.plugin.listener;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.ui.jcef.JBCefApp;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import org.jetbrains.annotations.NotNull;

/**
 * @author shuzijun
 */
public class SupportCheck implements StartupActivity, DumbAware {

    public static boolean isFirstProject = true;

    @Override
    public void runActivity(@NotNull Project project) {
        if (ApplicationManager.getApplication().isUnitTestMode() ||  !isFirstProject ) {
            return;
        }
        // 用 Throwable 兜底：JCEF 若因平台 module 缺失/停用而無法載入（NoClassDefFoundError 屬 Error），
        // 也絕不能讓 startup activity 把整個 IDE 啟動流程帶垮，只提示使用者即可。
        try {
            if (!JBCefApp.isSupported()) {
                Notifications.Bus.notify(new Notification(PluginConstant.NOTIFICATION_GROUP, "Not Support JCEF", "Your environment does not support JCEF, cannot use LeetCode Editor.Check the Registry 'ide.browser.jcef.enabled'.", NotificationType.ERROR));
            }
        } catch (Throwable t) {
            Notifications.Bus.notify(new Notification(PluginConstant.NOTIFICATION_GROUP, "Not Support JCEF", "Your environment does not support JCEF, cannot use LeetCode Editor.Check the Registry 'ide.browser.jcef.enabled'.", NotificationType.ERROR));
        }
    }
}
