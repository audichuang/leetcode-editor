package com.shuzijun.leetcode.plugin.window;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.JBTabsFactory;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.messages.MessageBusConnection;
import com.shuzijun.leetcode.plugin.listener.ConfigNotifier;
import com.shuzijun.leetcode.plugin.listener.LoginNotifier;
import com.shuzijun.leetcode.plugin.listener.QuestionStatusNotifier;
import com.shuzijun.leetcode.plugin.manager.NavigatorAction;
import com.shuzijun.leetcode.plugin.manager.QuestionManager;
import com.shuzijun.leetcode.plugin.model.Config;
import com.shuzijun.leetcode.plugin.model.QuestionView;
import com.shuzijun.leetcode.plugin.model.User;
import com.shuzijun.leetcode.plugin.setting.PersistentConfig;
import com.shuzijun.leetcode.plugin.setting.StatisticsData;
import com.shuzijun.leetcode.plugin.utils.DataKeys;
import com.shuzijun.leetcode.plugin.utils.HttpRequestUtils;
import com.shuzijun.leetcode.plugin.utils.LogUtils;
import com.shuzijun.leetcode.plugin.utils.URLUtils;
import com.shuzijun.leetcode.plugin.window.login.HttpLogin;
import com.shuzijun.leetcode.plugin.window.navigator.AllNavigatorPanel;
import com.shuzijun.leetcode.plugin.window.navigator.NavigatorPanel;
import com.shuzijun.leetcode.plugin.window.navigator.TopNavigatorPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author shuzijun
 */
public class NavigatorTabsPanel extends SimpleToolWindowPanel implements Disposable {

    private static final DisposableMap<String, NavigatorTabsPanel> NAVIGATOR_TABS_PANEL_DISPOSABLE_MAP = new DisposableMap<>();

    static {
        Disposer.register(ApplicationManager.getApplication(), NAVIGATOR_TABS_PANEL_DISPOSABLE_MAP);
    }

    private String id = UUID.randomUUID().toString();

    // used by start() background init/subscriptions; never used for Swing/UI work off-EDT.
    private final Project project;

    private SimpleToolWindowPanel[] navigatorPanels;
    private TabInfo[] tabInfos;

    private JBTabs tabs;

    // volatile: written on EDT (toggle()/constructor). Two read paths: uiDataSnapshot()
    // itself runs on EDT (snapshot time), but WindowFactory.getNavigatorAction(project)
    // -> getCurrentNavigatorAction() is reached from cross-context action callers whose
    // update() runs on BGT (PositionAction), so a plain int risks a stale read.
    private volatile int toggleIndex = 0;

    private volatile Map<String, User> userCache = new ConcurrentHashMap<>();

    public NavigatorTabsPanel(ToolWindow toolWindow, Project project) {
        super(Boolean.TRUE, Boolean.TRUE);

        this.project = project;

        navigatorPanels = new SimpleToolWindowPanel[3];
        tabInfos = new TabInfo[3];

        tabs = JBTabsFactory.createTabs(project, this);
        tabs.getPresentation().setHideTabs(true);

        NavigatorPanel navigatorPanel = new NavigatorPanel(toolWindow, project);
        navigatorPanels[0] = navigatorPanel;

        TabInfo tabInfo = new TabInfo(navigatorPanel);
        tabInfo.setText("page");
        tabInfos[0] = tabInfo;
        tabs.addTab(tabInfo);

        AllNavigatorPanel allNavigatorPanel = new AllNavigatorPanel(toolWindow, project);
        navigatorPanels[1] = allNavigatorPanel;

        TabInfo allTabInfo = new TabInfo(allNavigatorPanel);
        allTabInfo.setText("all");
        tabInfos[1] = allTabInfo;
        tabs.addTab(allTabInfo);

        TopNavigatorPanel topNavigatorPanel = new TopNavigatorPanel(toolWindow, project);
        navigatorPanels[2] = topNavigatorPanel;

        TabInfo topTabInfo = new TabInfo(topNavigatorPanel);
        topTabInfo.setText("codeTop");
        tabInfos[2] = topTabInfo;
        tabs.addTab(topTabInfo);

        Config config = PersistentConfig.getInstance().getInitConfig();
        if (config != null) {
            for (int i = 0; i < tabInfos.length; i++) {
                if (tabInfos[i].getText().equalsIgnoreCase(config.getNavigatorName())) {
                    tabs.select(tabInfos[i], true);
                    toggleIndex = i;
                    break;
                }
            }
        }

        setContent(tabs.getComponent());

        for (SimpleToolWindowPanel n : navigatorPanels) {
            if (n instanceof Disposable) {
                Disposer.register(this, (Disposable) n);
            }
        }

        NAVIGATOR_TABS_PANEL_DISPOSABLE_MAP.put(id, this);

    }

    /**
     * 背景初始化(登入狀態/統計)與事件訂閱。由 WindowFactory 在 registry
     * putUserData 完成、content 掛好之後呼叫——不能放建構子:pooled task 會經
     * SessionManager 讀 WindowFactory registry,建構子階段還沒註冊,拿到 null 會
     * 跳過 CN userSessionProgress 精修、把粗略統計寫進持久狀態。
     */
    void start() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            User user = getUser();
            if (user.isSignedIn()) {
                WindowFactory.updateTitle(project, user.getUsername());
                StatisticsData.refresh(project);
            } else {
                WindowFactory.updateTitle(project, "No login");
            }
        });
        MessageBusConnection messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
        messageBusConnection.subscribe(LoginNotifier.TOPIC, new LoginNotifier() {
            @Override
            public void login(Project notifierProject, String host) {
                User user = getUser();
                if (user.isSignedIn()) {
                    WindowFactory.updateTitle(project, user.getUsername());
                    StatisticsData.refresh(project);
                } else {
                    WindowFactory.updateTitle(project, "No login");
                }

            }

            @Override
            public void logout(Project notifierProject, String host) {
                WindowFactory.updateTitle(project, "No login");
            }
        });
        messageBusConnection.subscribe(ConfigNotifier.TOPIC, new ConfigNotifier() {
            @Override
            public void change(Config oldConfig, Config newConfig) {
                if (oldConfig != null && !oldConfig.getUrl().equalsIgnoreCase(newConfig.getUrl())) {
                    User user = getUser();
                    if (user.isSignedIn()) {
                        WindowFactory.updateTitle(project, user.getUsername());
                        StatisticsData.refresh(project);
                    } else {
                        WindowFactory.updateTitle(project, "No login");
                    }
                }
            }
        });
        messageBusConnection.subscribe(QuestionStatusNotifier.QUESTION_STATUS_TOPIC, (QuestionStatusNotifier) question -> StatisticsData.refresh(project));
    }

    public void toggle() {
        toggleIndex = (toggleIndex + 1) % 3;
        tabs.select(tabInfos[toggleIndex], true);
        Config config = PersistentConfig.getInstance().getInitConfig();
        if (config != null) {
            config.setNavigatorName(tabInfos[toggleIndex].getText());
            PersistentConfig.getInstance().setInitConfig(config);
        }
    }

    @NotNull
    public User getUser() {
        // 重開 IDE 後記憶體 client 是空的，先把磁碟 cookie 載回，下方走 QuestionManager.getUser() 才不會誤判未登入。
        // 冪等且低成本（hasSessionCookie 命中即 return），放這裡覆蓋所有 getUser 路徑。
        HttpLogin.restorePersistedCookies();
        Config config = PersistentConfig.getInstance().getInitConfig();
        if (config == null) {
            return new User();
        } else if (userCache.containsKey(config.getUrl())) {
            return userCache.get(config.getUrl());
        } else {
            String otherKey = NAVIGATOR_TABS_PANEL_DISPOSABLE_MAP.getOtherKey(id);
            if (otherKey == null || !((NavigatorTabsPanel) NAVIGATOR_TABS_PANEL_DISPOSABLE_MAP.get(otherKey)).userCache.containsKey(config.getUrl())) {
                User user = QuestionManager.getUser();
                // 有 session cookie 卻查到未登入 → 多半是網路抖動或 restore 時序（QuestionManager.getUser 對網路失敗
                // 與真未登入都回空 User，無法區分），不快取這個「假未登入」，下次 getUser 會重查而自我恢復；
                // 否則一次抖動就永久卡未登入、又得手動 login，本次自動登入形同白做。
                if (user.isSignedIn() || !HttpRequestUtils.hasSessionCookie()) {
                    userCache.put(config.getUrl(), user);
                }
                return user;
            } else {
                User user = ((NavigatorTabsPanel) NAVIGATOR_TABS_PANEL_DISPOSABLE_MAP.get(otherKey)).userCache.get(config.getUrl());
                userCache.put(config.getUrl(), user);
                return user;
            }
        }
    }

    // toggleIndex is volatile, so this is a safe read from any thread (BGT included).
    @Nullable
    public NavigatorAction getCurrentNavigatorAction() {
        SimpleToolWindowPanel panel = navigatorPanels[toggleIndex];
        if (panel instanceof NavigatorPanelAction) {
            return ((NavigatorPanelAction) panel).getNavigatorAction();
        }
        return null;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);
        sink.set(DataKeys.LEETCODE_PROJECTS_TABS, this);
        NavigatorAction navigatorAction = getCurrentNavigatorAction();
        if (navigatorAction != null) {
            sink.set(DataKeys.LEETCODE_PROJECTS_NAVIGATORACTION, navigatorAction);
            // Swing JTable read (getSelectedRowData()) must happen here, on EDT during the
            // platform's DataContext snapshot — never inside a BGT action/group's getChildren().
            Object rowData = navigatorAction.getSelectedRowData();
            if (rowData instanceof QuestionView) {
                sink.set(DataKeys.LEETCODE_PROJECTS_SELECTED_QUESTION, (QuestionView) rowData);
            }
        }
    }

    @Override
    public void dispose() {
        // 子面板已在建構子用 Disposer.register(this, ...) 接管，平台 Disposer 會先銷毀子節點再回呼這裡；
        // 別再手動逐一 dispose，否則雙重 disposal。
        // WindowFactory registry 的清除不在這裡：WindowFactory 在 putUserData 後另外
        // Disposer.register 了一個 compare-and-clear 子節點（LIFO，比建構子註冊的所有
        // 子面板先跑），BGT 在 disposal 期間查 registry 會先於任何 UI 拆除就拿到 null——
        // 若等到這個父回呼才清，會留下「子面板拆到一半、registry 還回傳本 panel」的窗口。
        NAVIGATOR_TABS_PANEL_DISPOSABLE_MAP.remove(id);
    }

    // 輪詢重試次數/間隔上限：指數退避 500ms→...→8s 封頂，且不超過 LOAD_USER_DEADLINE_MILLIS 總時長，
    // 取代舊版「鎖內最多 51 次 GraphQL、光 sleep 就 36 秒」。
    private static final int LOAD_USER_MAX_ATTEMPTS = 6;
    private static final long LOAD_USER_MAX_DELAY_MILLIS = 8_000L;
    private static final long LOAD_USER_DEADLINE_MILLIS = 30_000L;

    public static void loadUser(boolean login) {
        // 登入/登出後帳號狀態已變，殘留的 HTTP 快取（userStatus、清單）一律作廢；
        // 這兩個呼叫本身 thread-safe（Guava cache / volatile 計數器），不需要靠外層鎖保護，
        // RefreshAction/ProgressAction 也是直接無鎖呼叫它們。
        HttpRequestUtils.invalidateCache();
        QuestionManager.invalidateAll();
        // 網路輪詢與 sleep 移出鎖外：不同 project 併發呼叫 loadUser(true) 不再彼此排隊等 30 秒；
        // 只有下面把結果寫回各面板快取這一步需要跟其他 loadUser 呼叫互斥。
        User user = login ? waitForSignedInUser() : new User();
        publishUser(user);
    }

    private static User waitForSignedInUser() {
        long deadline = System.currentTimeMillis() + LOAD_USER_DEADLINE_MILLIS;
        long delay = 500L;
        User user = new User();
        for (int attempt = 0; attempt < LOAD_USER_MAX_ATTEMPTS; attempt++) {
            user = QuestionManager.getUser();
            if (user.isSignedIn()) {
                return user;
            }
            if (attempt == LOAD_USER_MAX_ATTEMPTS - 1 || System.currentTimeMillis() + delay >= deadline) {
                break;
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return user;
            }
            delay = Math.min(delay * 2, LOAD_USER_MAX_DELAY_MILLIS);
        }
        LogUtils.LOG.warn("User data is not synchronized");
        return user;
    }

    private static synchronized void publishUser(User user) {
        Collection<NavigatorTabsPanel> collection = NAVIGATOR_TABS_PANEL_DISPOSABLE_MAP.values();
        for (NavigatorTabsPanel navigatorTabsPanel : collection) {
            navigatorTabsPanel.userCache.put(URLUtils.getLeetcodeHost(), user);
        }
    }

    public static class DisposableMap<K, V> extends HashMap implements Disposable {
        @Override
        public synchronized Object put(Object key, Object value) {
            return super.put(key,value);
        }

        public synchronized K getOtherKey(K key){
            K otherKey = null;
            for (Object k : this.keySet()) {
                if (!k.equals(key)) {
                    otherKey = (K) k;
                    break;
                }
            }
            return otherKey;
        }

        @Override
        public void dispose() {
            for (Object value : values()) {
                if (value instanceof Disposable) {
                    ((Disposable) value).dispose();
                }
            }
        }
    }
}
