package com.shuzijun.leetcode.plugin.window.login;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.shuzijun.leetcode.plugin.listener.LoginNotifier;
import com.shuzijun.leetcode.plugin.manager.NavigatorAction;
import com.shuzijun.leetcode.plugin.model.Config;
import com.shuzijun.leetcode.plugin.model.HttpRequest;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import com.shuzijun.leetcode.plugin.model.User;
import com.shuzijun.leetcode.plugin.setting.PersistentConfig;
import com.shuzijun.leetcode.plugin.utils.*;
import com.shuzijun.leetcode.plugin.window.NavigatorTabsPanel;
import com.shuzijun.leetcode.plugin.window.WindowFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author shuzijun
 */
public class HttpLogin {
    // 已知限制：CN 帳密登入路徑。成功後只 loadUser + notifier，未走 loginSuccess 持久化 cookie（不像 JCEF/貼cookie），
    // 故此路徑登入者重開 IDE 無法被 restorePersistedCookies 自動恢復、仍需手動登入。
    // 暫不補：CN 匿名被 Cloudflare 擋、主線用 .com JCEF；若日後復用此路徑，需讓成功分支也持久化目前 cookie-store 快照。
    public static boolean ajaxLogin(Config config, NavigatorAction navigatorAction, Project project) {

        if (!URLUtils.isCn()) {
            return Boolean.FALSE;
        }

        if (StringUtils.isBlank(PersistentConfig.getInstance().getPassword(config.getLoginName()))) {
            return Boolean.FALSE;
        }

        try {
            HttpEntity ent = MultipartEntityBuilder.create()
                    .addTextBody("csrfmiddlewaretoken", HttpRequestUtils.getToken() == null ? "" : HttpRequestUtils.getToken())
                    .addTextBody("login", config.getLoginName())
                    .addTextBody("password", PersistentConfig.getInstance().getPassword(config.getLoginName()))
                    .addTextBody("next", "/problems")
                    .build();
            HttpResponse response = HttpRequest.builderPost(URLUtils.getLeetcodeLogin(), ent.getContentType().getValue())
                    .body(new String(ent.getContent().readAllBytes(), StandardCharsets.UTF_8))
                    .addHeader("x-requested-with", "XMLHttpRequest")
                    .addHeader("accept", "*/*").request();

            String body = response.getBody();

            if ((response.getStatusCode() == 200 || response.getStatusCode() == 302)) {
                if (StringUtils.isNotBlank(body) && body.startsWith("{")) {
                    JSONObject jsonObject = JSONObject.parseObject(body);
                    JSONArray jsonArray = jsonObject.getJSONObject("form").getJSONArray("errors");
                    if (jsonArray.isEmpty()) {
                        MessageUtils.getInstance(project).showInfoMsg("info", PropertiesUtils.getInfo("login.success"));
                        NavigatorTabsPanel.loadUser(true);
                        ApplicationManager.getApplication().getMessageBus().syncPublisher(LoginNotifier.TOPIC).login(project, config.getUrl());
                        examineEmail(project);
                        return Boolean.TRUE;
                    } else {
                        MessageUtils.getInstance(project).showInfoMsg("info", StringUtils.join(jsonArray, ","));
                        return Boolean.FALSE;
                    }
                } else if (StringUtils.isBlank(body)) {
                    MessageUtils.getInstance(project).showInfoMsg("info", PropertiesUtils.getInfo("login.success"));
                    NavigatorTabsPanel.loadUser(true);
                    ApplicationManager.getApplication().getMessageBus().syncPublisher(LoginNotifier.TOPIC).login(project, config.getUrl());
                    examineEmail(project);
                    return Boolean.TRUE;
                } else {
                    HttpRequestUtils.resetHttpclient();
                    MessageUtils.getInstance(project).showInfoMsg("info", PropertiesUtils.getInfo("login.unknown"));
                    //SentryUtils.submitErrorReport(null, String.format("login.unknown:\nStatusCode:%s\nbody:%s", response.getStatusCode(), body));
                    return Boolean.FALSE;
                }
            } else if (response.getStatusCode() == 400) {
                LogUtils.LOG.info("login 400:" + body);
                try {
                    JSONObject jsonObject = JSONObject.parseObject(body);
                    MessageUtils.getInstance(project).showInfoMsg("info", StringUtils.join(jsonObject.getJSONObject("form").getJSONArray("errors"), ","));
                } catch (Exception ignore) {

                }
                return Boolean.FALSE;
            } else {
                HttpRequestUtils.resetHttpclient();
                MessageUtils.getInstance(project).showInfoMsg("info", PropertiesUtils.getInfo("login.unknown"));
                //SentryUtils.submitErrorReport(null, String.format("login.unknown:\nStatusCode:%s\nbody:%s", response.getStatusCode(), body));
                return Boolean.FALSE;
            }
        } catch (Exception e) {
            LogUtils.LOG.error("登陆错误", e);
            MessageUtils.getInstance(project).showInfoMsg("info", PropertiesUtils.getInfo("login.failed"));
            return Boolean.FALSE;
        }
    }

    public static void examineEmail(Project project) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
                try {
                    User user = WindowFactory.getUser(project);
                    if (user == null) {
                        return;
                    }
                    if (user.isVerified() || user.isPhoneVerified()) {
                        return;
                    }
                    MessageUtils.getInstance(project).showWarnMsg("info", PropertiesUtils.getInfo("user.email"));
                } catch (Exception i) {
                    LogUtils.LOG.error("验证邮箱错误");
                }
            }
        });
    }

    public static void loginSuccess(Project project, List<HttpCookie> cookieList) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, PluginConstant.ACTION_PREFIX + ".loginSuccess", false) {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                Config config = PersistentConfig.getInstance().getInitConfig();
                config.addCookie(config.getUrl() + config.getLoginName(), CookieUtils.httpCookieToJSONString(cookieList));
                PersistentConfig.getInstance().setInitConfig(config);
                MessageUtils.getInstance(project).showInfoMsg("info", PropertiesUtils.getInfo("login.success"));
                NavigatorTabsPanel.loadUser(true);
                ApplicationManager.getApplication().getMessageBus().syncPublisher(LoginNotifier.TOPIC).login(project, config.getUrl());
                examineEmail(project);
            }
        });
    }

    // 開面板時把磁碟持久化的 cookie 載回記憶體 client（lc-sdk client 是 static 記憶體物件，重開 IDE 就空）。
    // 只做 hydration，不呼叫 isLogin、不發 LoginNotifier——真正的登入驗證交給呼叫端既有的 getUser()。
    // synchronized + 先查 hasSessionCookie：序列化 restore 彼此、且已有 session 就短路。
    // 注意：這把鎖「只」保證 restore 自身冪等，不涵蓋 LoginAction/LogoutAction 的 cookie 寫入；
    // logout 已把清磁碟排在清記憶體之前來避開與 restore 的交錯（見 LogoutAction）。
    public static synchronized void restorePersistedCookies() {
        if (HttpRequestUtils.hasSessionCookie()) {
            return;
        }
        Config config = PersistentConfig.getInstance().getInitConfig();
        if (config == null || StringUtils.isBlank(config.getLoginName())) {
            return;
        }
        String cookieJson = config.getCookie(config.getUrl() + config.getLoginName());
        if (StringUtils.isBlank(cookieJson)) {
            return;
        }
        try {
            HttpRequestUtils.setCookieIfAbsent(CookieUtils.toHttpCookie(cookieJson));
        } catch (Exception e) {
            // 持久化 cookie 解析失敗（資料損壞等）：記 warning 但不中斷，避免每次開面板靜默失敗又查無原因。
            LogUtils.LOG.warn("restorePersistedCookies failed to parse persisted cookie", e);
        }
    }

    // logout 專用：與 restorePersistedCookies 共用 HttpLogin.class 鎖，讓「清磁碟 + 清記憶體」與 restore 的
    // 「讀磁碟 + 寫記憶體」互斥。否則 restore 可能在 logout 清除後、用它稍早讀到的舊 cookieJson 把 cookie 寫回記憶體，
    // 若遠端 logout 未生效更會復活仍有效的 session。
    public static synchronized void clearCookiesOnLogout(Config config) {
        if (config != null) {
            config.addCookie(config.getUrl() + config.getLoginName(), null);
            PersistentConfig.getInstance().setInitConfig(config);
        }
        HttpRequestUtils.resetHttpclient();
    }

    public static boolean isEnabledJcef() {
        Config config = PersistentConfig.getInstance().getInitConfig();
        return config != null && !config.isCookie() && isSupportedJcef();
    }

    public static boolean isSupportedJcef() {
        try {
            Class<?> JBCefAppClass = Class.forName("com.intellij.ui.jcef.JBCefApp");
            Method method = JBCefAppClass.getMethod("isSupported");
            return (boolean) method.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException e) {
            return Boolean.FALSE;
        }
    }

}
