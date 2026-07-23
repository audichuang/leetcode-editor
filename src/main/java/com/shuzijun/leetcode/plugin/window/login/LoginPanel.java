package com.shuzijun.leetcode.plugin.window.login;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.jcef.JCEFHtmlPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import com.shuzijun.leetcode.plugin.utils.*;
import org.apache.commons.lang3.StringUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCookieVisitor;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefCookie;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author shuzijun
 */
public class LoginPanel extends DialogWrapper {

    private BorderLayoutPanel panel = JBUI.Panels.simplePanel();

    private JTextArea cookieText = new JBTextArea();

    private JcefPanel jcefPanel;

    private Project project;

    private Action okAction;

    public LoginPanel(@Nullable Project project) {
        super(project, null, false, IdeModalityType.IDE, !HttpLogin.isEnabledJcef());
        this.project = project;
        if (HttpLogin.isEnabledJcef()) {
            okAction = new OkAction() {
            };
            try {
                jcefPanel = new JcefPanel(project, okAction);
            } catch (IllegalArgumentException e) {
                jcefPanel = new JcefPanel(project, okAction,true);
            }
            Disposer.register(getDisposable(),jcefPanel);
            jcefPanel.getComponent().setMinimumSize(JBUI.size(1000, 500));
            jcefPanel.getComponent().setPreferredSize(JBUI.size(1000, 500));
            panel.addToCenter(new JBScrollPane(jcefPanel.getComponent(), JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER));

        } else {
            cookieText.setLineWrap(true);
            cookieText.setMinimumSize(JBUI.size(400, 200));
            cookieText.setPreferredSize(JBUI.size(400, 200));
            panel.addToCenter(new JBScrollPane(cookieText, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
            okAction = new OkAction() {
                @Override
                protected void doAction(ActionEvent e) {
                    String cookiesString = cookieText.getText();
                    if (StringUtils.isBlank(cookiesString)) {
                        JOptionPane.showMessageDialog(null, "cookie is null");
                        return;
                    }
                    final List<HttpCookie> cookieList = new ArrayList<>();
                    String[] cookies = cookiesString.split(";");
                    for (String cookieString : cookies) {
                        String[] cookie = cookieString.trim().split("=");
                        if (cookie.length >= 2) {
                            try {
                                HttpCookie basicClientCookie = new HttpCookie(cookie[0], cookie[1]);
                                basicClientCookie.setDomain("." + URLUtils.getLeetcodeHost());
                                basicClientCookie.setPath("/");
                                cookieList.add(basicClientCookie);
                            } catch (IllegalArgumentException ignore) {

                            }
                        }
                    }
                    HttpRequestUtils.setCookie(cookieList);

                    ProgressManager.getInstance().run(new Task.Backgroundable(project, PluginConstant.ACTION_PREFIX + ".loginSuccess", false) {
                        @Override
                        public void run(@NotNull ProgressIndicator progressIndicator) {
                            // 驗證是異步背景工作，doAction() 這裡不能同步等待，故關 dialog/顯示結果一律延到驗證完成後、
                            // 於 invokeLater 內做；失敗時保持 dialog 開啟讓使用者重試，不呼叫 close()。
                            boolean login = HttpRequestUtils.isLogin(project);
                            ApplicationManager.getApplication().invokeLater(() -> {
                                if (login) {
                                    HttpLogin.loginSuccess(project, cookieList);
                                    LoginPanel.this.close(DialogWrapper.OK_EXIT_CODE);
                                } else {
                                    Messages.showErrorDialog(project, PropertiesUtils.getInfo("login.failed"), "login");
                                }
                            });
                        }
                    });
                }
            };
            okAction.putValue(Action.NAME, "login");
        }

        setModal(false);
        init();
        setTitle("login");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return panel;
    }

    @NotNull
    @Override
    protected Action getOKAction() {
        return okAction;
    }

    @NotNull
    @Override
    protected Action[] createActions() {
        Action helpAction = new AbstractAction("help") {
            @Override
            public void actionPerformed(ActionEvent e) {
                BrowserUtil.browse("https://github.com/shuzijun/leetcode-editor/blob/master/doc/LoginHelp.md");
            }

        };
        Action[] actions = new Action[]{helpAction, this.getOKAction(), this.getCancelAction()};
        return actions;
    }




    private static class JcefPanel extends JCEFHtmlPanel {


        private CefLoadHandlerAdapter cefLoadHandler;

        private Project project;

        private Action okAction;

        public JcefPanel(Project project, Action okAction, boolean old) {
            super( null);
            this.project = project;
            this.okAction = okAction;
            init();
        }

        public JcefPanel(Project project, Action okAction) {
            super(null, null);
            this.project = project;
            this.okAction = okAction;
            init();
        }

        private void init(){
            try {
                initLoadHandlerAndLoad();
            } catch (Throwable t) {
                // ponytail: 中途拋例外時 constructor 不會返回，外層(LoginPanel)沒機會 dispose 已配置的 browser；
                // 直接複用 dispose()（已做 null-guard）做 best-effort 清理再往外拋，保留原例外型別給外層 catch(IllegalArgumentException) 用
                dispose();
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else if (t instanceof Error) {
                    throw (Error) t;
                } else {
                    throw new RuntimeException(t);
                }
            }
        }

        private void initLoadHandlerAndLoad(){
            getJBCefClient().addLoadHandler(cefLoadHandler = new CefLoadHandlerAdapter() {

                // AtomicBoolean：JCEF 回呼可能在不同執行緒觸發（IO/UI/remote pool），plain boolean 無跨緒可見性保證
                final AtomicBoolean successDispose = new AtomicBoolean(false);

                @Override
                public void onLoadError(CefBrowser browser, CefFrame frame, CefLoadHandler.ErrorCode errorCode, String errorText, String failedUrl) {
                    if (!successDispose.get()) {
                        MessageUtils.getInstance(project).showWarnMsg("", "The page failed to load, please check the network and open it again");
                    }
                }

                @Override
                public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                    // 登入成功後不再重複掃 cookie / 打 isLogin：此回呼每次頁面載入狀態變化都會觸發，成功後掃描純屬浪費網路請求。
                    if (successDispose.get()) {
                        return;
                    }
                    getJBCefCookieManager().getCefCookieManager().visitAllCookies(new CefCookieVisitor() {

                        private List<HttpCookie> cookieList = new ArrayList<>();

                        @Override
                        public boolean visit(CefCookie cefCookie, int count, int total, BoolRef boolRef) {

                            if (cefCookie.domain.contains("leetcode")) {
                                HttpCookie cookie = new HttpCookie(cefCookie.name, cefCookie.value);
                                cookie.setDomain(cefCookie.domain);
                                cookie.setPath(cefCookie.path);
                                cookieList.add(cookie);
                            }
                            if (count == total - 1) {
                                if (cookieList.stream().anyMatch(cookie -> cookie.getName().equals("LEETCODE_SESSION")) &&
                                        !HttpRequestUtils.isLogin(project)) {
                                    HttpRequestUtils.setCookie(cookieList);
                                    if (HttpRequestUtils.isLogin(project)) {
                                        HttpLogin.loginSuccess(project, cookieList);
                                        MessageUtils.getInstance(project).showWarnMsg("", PropertiesUtils.getInfo("browser.login.success"));
                                        ApplicationManager.getApplication().invokeLater(() -> okAction.actionPerformed(null));
                                        successDispose.set(true);
                                    } else {
                                        cookieList.clear();
                                        LogUtils.LOG.info("login failure");
                                    }
                               }
                            }
                            return true;
                        }
                    });
                }
            }, getCefBrowser());
            loadURL(URLUtils.getLeetcodeLogin());
        }

        @Override
        public void dispose() {
            try {
                if (cefLoadHandler != null) {
                    try {
                        getJBCefClient().removeLoadHandler(cefLoadHandler, getCefBrowser());
                    } catch (Throwable t) {
                        LogUtils.LOG.warn("failed to remove load handler on dispose", t);
                    } finally {
                        cefLoadHandler = null;
                    }
                }
                try {
                    // ponytail: deleteCookies(url, boolean) is deprecated in 262; deleteCookies(url, cookieName)
                    // with cookieName=null means "delete all cookies for this url", same effect as the old overload.
                    getJBCefBrowser(getCefBrowser()).getJBCefCookieManager().deleteCookies(URLUtils.leetcode, (String) null);
                    getJBCefBrowser(getCefBrowser()).getJBCefCookieManager().deleteCookies(URLUtils.leetcodecn, (String) null);
                } catch (Exception e) {
                    // ponytail: cef_server may not be established yet when the dialog closes early (#754); clearing
                    // cookies is best-effort, next login overwrites them, so just log and continue disposing.
                    LogUtils.LOG.warn("failed to delete jcef cookies on dispose", e);
                }
            } finally {
                super.dispose();
            }
        }
    }
}
