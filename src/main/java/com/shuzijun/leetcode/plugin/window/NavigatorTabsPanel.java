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

    // held only to clear WindowFactory.NAVIGATOR_PANEL_KEY in dispose(); never used for Swing/UI work.
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

        for (SimpleToolWindowPanel n : navigatorPanels) {
            if (n instanceof Disposable) {
                Disposer.register(this, (Disposable) n);
            }
        }

        NAVIGATOR_TABS_PANEL_DISPOSABLE_MAP.put(id, this);

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
        // compare-and-clear：只清「還是自己」的註冊，避免萬一平台先建立/註冊了新 panel、才 dispose 舊
        // panel 時，把存活 panel 的註冊給抹掉（否則 WindowFactory.getUser/getNavigatorAction 對該
        // project 會永遠回 null）。與下面 DisposableMap.remove(id) 只清自己 id 的語意一致。
        if (project.getUserData(WindowFactory.NAVIGATOR_PANEL_KEY) == this) {
            project.putUserData(WindowFactory.NAVIGATOR_PANEL_KEY, null);
        }
        NAVIGATOR_TABS_PANEL_DISPOSABLE_MAP.remove(id);
    }

    public static synchronized void loadUser(boolean login) {
        // 登入/登出後帳號狀態已變，殘留的 HTTP 快取（userStatus、清單）一律作廢
        HttpRequestUtils.invalidateCache();
        // QuestionManager 的 questionCache/questionAllCache/questionIndexCache/dayMap 也是舊帳號資料，一併清空
        QuestionManager.invalidateAll();
        User user = null;
        if (login) {
            for (int i = 0; i <= 50; i++) {
                user = QuestionManager.getUser();
                if (!user.isSignedIn()) {
                    try {
                        Thread.sleep(500 + (i / 10 * 100));
                    } catch (InterruptedException ignore) {
                    }
                } else {
                    break;
                }
                if(i == 50){
                    LogUtils.LOG.warn("User data is not synchronized");
                }
            }
        } else {
            user = new User();
        }
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
