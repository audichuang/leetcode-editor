package com.shuzijun.leetcode.plugin.utils;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.shuzijun.leetcode.plugin.model.Config;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import com.shuzijun.leetcode.plugin.setting.PersistentConfig;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import io.sentry.context.Context;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.UserBuilder;
import io.sentry.event.interfaces.ExceptionInterface;

import java.util.HashMap;
import java.util.Map;

/**
 * @author shuzijun
 */
public class SentryUtils {

    private static volatile SentryClient sentryClient;

    private static SentryClient getSentryClient() {
        SentryClient client = sentryClient;
        if (client == null) {
            synchronized (SentryUtils.class) {
                client = sentryClient;
                if (client == null) {
                    client = SentryClientFactory.sentryClient("https://ac9e2d69c3294870848cee5b1b23ad51@sentry.io/1534194");
                    sentryClient = client;
                }
            }
        }
        return client;
    }

    public static void submitErrorReport(Throwable error, String description) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> doSubmitErrorReport(error, description));
    }

    private static void doSubmitErrorReport(Throwable error, String description) {

        final SentryClient sentry = getSentryClient();

        final ApplicationInfo applicationInfo = ApplicationInfo.getInstance();

        final Map<String, Object> os = new HashMap<>();
        os.put("name", SystemInfo.OS_NAME);
        os.put("version", SystemInfo.OS_VERSION);
        os.put("kernel_version", SystemInfo.OS_ARCH);

        final Map<String, Object> runtime = new HashMap<>();
        final String ideName = applicationInfo.getBuild().getProductCode();
        runtime.put("name", ideName);
        runtime.put("version", applicationInfo.getFullVersion());

        final Map<String, Map<String, Object>> contexts = new HashMap<>();
        contexts.put("os", os);
        contexts.put("runtime", runtime);

        // ponytail: build the event inline instead of addBuilderHelper — helpers would
        // accumulate on the reused client and leak descriptions across reports
        EventBuilder eventBuilder = new EventBuilder().withContexts(contexts);
        if (error == null) {
            eventBuilder.withLevel(Event.Level.INFO).withMessage(description);
        } else {
            eventBuilder.withLevel(Event.Level.ERROR).withMessage(error.getMessage())
                    .withSentryInterface(new ExceptionInterface(error));
        }
        if (!StringUtil.isEmptyOrSpaces(description)) {
            eventBuilder.withMessage(description);
            eventBuilder.withTag("with-description", "true");
        }

        final Context context = sentry.getContext();
        // ponytail: pooled thread 重用同一 static SentryClient，ThreadLocalContextManager 不會
        // 自動清掉上一次 report 留下的 user/tags，先 clear() 再填本次資料，避免跨 report 洩漏舊 context
        context.clear();

        final Config config = PersistentConfig.getInstance().getInitConfig();
        if (config != null) {

            UserBuilder userBuilder = new UserBuilder();
            userBuilder.setId(config.getId());

            Map<String, Object> userConfig = new HashMap<>();
            userConfig.put("version", config.getVersion());
            userConfig.put("codeType", config.getCodeType());
            userConfig.put("url", config.getUrl());
            userConfig.put("proxy", config.getProxy());
            userConfig.put("customCode", config.getCustomCode());
            userConfig.put("customFileName", config.getCustomFileName());
            userConfig.put("customTemplate", config.getCustomTemplate());
            userBuilder.setData(userConfig);
            context.setUser(userBuilder.build());

        }
        context.addTag("javaVersion", SystemInfo.JAVA_RUNTIME_VERSION);
        context.addTag("pluginVersion", PluginManagerCore.getPlugin(PluginId.getId(PluginConstant.PLUGIN_ID)).getVersion());

        sentry.sendEvent(eventBuilder);

    }

}
