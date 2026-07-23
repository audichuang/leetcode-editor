package com.shuzijun.leetcode.plugin.utils;

import com.google.common.base.Utf8;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.util.net.JdkProxyProvider;
import com.intellij.util.net.ProxyConfiguration;
import com.intellij.util.net.ProxySettings;
import com.shuzijun.lc.LcClient;
import com.shuzijun.lc.errors.LcException;
import com.shuzijun.lc.http.DefaultExecutoHttp;
import com.shuzijun.lc.http.HttpClient;
import com.shuzijun.leetcode.plugin.model.HttpRequest;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import okhttp3.Challenge;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Route;
import org.jetbrains.annotations.NotNull;

import java.net.Authenticator;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author shuzijun
 */
public class HttpRequestUtils {

    // ponytail: 上限單位從「筆數」改成「位元組」，避免少數大 body（如 allQuestions ~2MB）把 200 個 slot 撐成數百MB；
    // weigher 用 UTF-8 位元組數（而非 String.length() 的 UTF-16 字元數），CJK body 才不會被低估近半；
    // 用 Utf8.encodedLength 算位元組數而不 getBytes()，避免每次 cache put 都多配一份等同 body 大小的 byte[] 副本；
    // 其餘 LRU/TTL/per-key 載入鎖語意不變，只是淘汰依據換了計量單位
    private static final Cache<HttpRequest, HttpResponse> httpResponseCache = CacheBuilder.newBuilder()
            .maximumWeight(32L * 1024 * 1024)
            .weigher((HttpRequest k, HttpResponse v) -> v.getBody() == null ? 0 : Utf8.encodedLength(v.getBody()))
            .expireAfterWrite(30, TimeUnit.SECONDS).build();

    private static MyExecutorHttp executorHttp = new MyExecutorHttp();
    private static LcClient enLcClient = LcClient.builder(HttpClient.SiteEnum.EN).executorHttp(executorHttp).build();
    private static LcClient cnLcClient = LcClient.builder(HttpClient.SiteEnum.CN).executorHttp(executorHttp).build();

    private static HttpResponse buildResp(com.shuzijun.lc.http.HttpResponse response, HttpResponse httpResponse) {
        httpResponse.setUrl(response.getHttpRequest().getUrl());
        httpResponse.setStatusCode(response.getStatusCode());
        httpResponse.setBody(response.getBody());
        return httpResponse;
    }

    private static Map<String, String> getHeader(String url) {
        if (url.contains(HttpClient.SiteEnum.EN.defaultEndpoint)) {
            return enLcClient.getClient().getHeader();
        } else {
            return cnLcClient.getClient().getHeader();
        }
    }

    @NotNull
    public static HttpResponse executeGet(HttpRequest httpRequest) {

        return CacheProcessor.processor(httpRequest, request -> {

            HttpResponse httpResponse = new HttpResponse();
            try {
                com.shuzijun.lc.http.HttpRequest.HttpRequestBuilder builder = com.shuzijun.lc.http.HttpRequest.
                        builderGet(request.getUrl()).body(request.getBody()).addHeader(getHeader(request.getUrl()));
                if (request.getHeader() != null) {
                    builder.addHeader(request.getHeader());
                }
                return buildResp(executorHttp.executeGet(builder.build()), httpResponse);

            } catch (LcException e) {
                LogUtils.LOG.warn("HttpRequestUtils request error:", e);
                httpResponse.setStatusCode(-1);
            }
            return httpResponse;
        });


    }

    @NotNull
    public static HttpResponse executePost(HttpRequest httpRequest) {
        return CacheProcessor.processor(httpRequest, request -> {
            HttpResponse httpResponse = new HttpResponse();
            try {
                com.shuzijun.lc.http.HttpRequest.HttpRequestBuilder builder = com.shuzijun.lc.http.HttpRequest.
                        builderPost(request.getUrl(), request.getContentType()).body(request.getBody()).addHeader(getHeader(request.getUrl()));
                if (request.getHeader() != null) {
                    builder.addHeader(request.getHeader());
                }
                return buildResp(executorHttp.executePost(builder.build()), httpResponse);
            } catch (LcException e) {
                LogUtils.LOG.warn("HttpRequestUtils request error:", e);
                httpResponse.setStatusCode(-1);
            }
            return httpResponse;
        });
    }

    public static HttpResponse executePut(HttpRequest httpRequest) {
        return CacheProcessor.processor(httpRequest, request -> {
            HttpResponse httpResponse = new HttpResponse();
            try {
                com.shuzijun.lc.http.HttpRequest.HttpRequestBuilder builder = com.shuzijun.lc.http.HttpRequest.
                        builderPut(request.getUrl(), request.getContentType()).body(request.getBody()).addHeader(getHeader(request.getUrl()));
                if (request.getHeader() != null) {
                    builder.addHeader(request.getHeader());
                }
                return buildResp(executorHttp.executePut(builder.build()), httpResponse);
            } catch (LcException e) {
                LogUtils.LOG.warn("HttpRequestUtils request error:", e);
                httpResponse.setStatusCode(-1);
            }
            return httpResponse;
        });
    }

    public static String getToken() {
        Map<String, String> headerMap = getHeader(URLUtils.getLeetcodeHost());
        return headerMap.get("x-csrftoken");
    }

    // isLogin 原本每次呼叫都真的打一個 GET；這裡加短 TTL 快取 + in-flight 合併：
    // 併發呼叫共用同一把 lock，只有第一個真的發請求，其餘在鎖內重新檢查會直接讀到剛寫入的快取。
    // 用獨立的 lock 物件而非 static synchronized，避免這個可能較慢的網路呼叫卡住 setCookie/resetHttpclient 等 cookie 寫入。
    // 快取失效統一掛在 setCookie/setCookieIfAbsent/resetHttpclient（唯三會改變登入狀態的地方）——
    // 涵蓋登入成功、登出、cookie 貼上/JCEF 掃描等所有路徑，不需要在每個外部呼叫端各補一次 invalidate。
    private static final long AUTH_CACHE_TTL_MILLIS = 30_000L;
    private static final Object AUTH_CACHE_LOCK = new Object();
    private static volatile long authCacheTimestamp = -1L;
    // 站點切換（leetcode.com ↔ leetcode.cn）不會經過 setCookie/resetHttpclient，
    // 快取必須連 host 一起記，host 不符視為 miss，否則切站後最多殘留 TTL 內的舊站登入狀態。
    private static volatile String authCacheHost = null;
    // 只快取「已登入」：登入可以不經過任何失效點（CN 帳密 ajaxLogin 的 cookie 直接寫進 OkHttp cookie store），
    // 快取 false 會讓剛登入的帳號最多 30 秒仍被當未登入——這也是登入自動恢復「不快取假未登入」不變式的要求。
    // generation：失效走的是 HttpRequestUtils.class 鎖（setCookie 等），與這裡的 AUTH_CACHE_LOCK 不同把，
    // 在途的驗證若跨過一次失效，結果不得回寫（否則登出後可殘留 30 秒的舊 true）。
    private static volatile long authCacheGeneration = 0L;

    public static boolean isLogin(Project project) {
        String host = URLUtils.getLeetcodeHost();
        long now = System.currentTimeMillis();
        if (authCacheTimestamp >= 0 && now - authCacheTimestamp < AUTH_CACHE_TTL_MILLIS && host.equals(authCacheHost)) {
            return true;
        }
        synchronized (AUTH_CACHE_LOCK) {
            now = System.currentTimeMillis();
            if (authCacheTimestamp >= 0 && now - authCacheTimestamp < AUTH_CACHE_TTL_MILLIS && host.equals(authCacheHost)) {
                return true;
            }
            long gen = authCacheGeneration;
            HttpResponse response = HttpRequest.builderGet(URLUtils.getLeetcodePoints()).request();
            boolean login = response.getStatusCode() == 200;
            if (login && gen == authCacheGeneration) {
                authCacheHost = host;
                authCacheTimestamp = System.currentTimeMillis();
            }
            return login;
        }
    }

    private static void invalidateAuthCache() {
        // 呼叫端（setCookie/setCookieIfAbsent/resetHttpclient）都在 HttpRequestUtils.class 鎖內，遞增已序列化
        authCacheGeneration++;
        authCacheTimestamp = -1L;
    }

    // 以下 cookie 記憶體操作均 synchronized(HttpRequestUtils.class)：讓 clearCookie+addCookie 這種複合寫入原子化，
    // 消除 login(貼cookie/JCEF) 的 setCookie 與 restore/logout 並發時的 partial write／先清後寫競態。
    // 鎖序：restore/logout 先持 HttpLogin.class 再進此處持 HttpRequestUtils.class；此處不回呼 HttpLogin，無反向鎖序、不會死結。
    public static synchronized void setCookie(List<HttpCookie> cookieList) {
        enLcClient.getClient().cookieStore().clearCookie(URLUtils.getLeetcodeHost());
        enLcClient.getClient().cookieStore().addCookie(URLUtils.getLeetcodeHost(), cookieList);
        invalidateAuthCache();
    }

    // 僅在記憶體尚無 session cookie 時才載入（restore 專用）：「檢查 + 載入」在同一把 HttpRequestUtils.class 鎖內原子完成。
    // 這保證並發的 login setCookie(新) 與 restore(舊) 不論先後，login 的新 cookie 都不會被 restore 舊值覆蓋：
    // login 先 → restore 見 session 已在而跳過；restore 先 → login 無條件 setCookie 覆蓋。
    public static synchronized void setCookieIfAbsent(List<HttpCookie> cookieList) {
        if (enLcClient.getClient().cookieStore().getCookie(URLUtils.getLeetcodeHost(), "LEETCODE_SESSION") != null) {
            return;
        }
        enLcClient.getClient().cookieStore().clearCookie(URLUtils.getLeetcodeHost());
        enLcClient.getClient().cookieStore().addCookie(URLUtils.getLeetcodeHost(), cookieList);
        invalidateAuthCache();
    }

    public static synchronized void resetHttpclient() {
        enLcClient.getClient().cookieStore().clearCookie(URLUtils.getLeetcodeHost());
        invalidateAuthCache();
    }

    // 記憶體 client 是否已握有登入用的 session cookie（判斷 restore 是否需要跑）。
    // 用 LEETCODE_SESSION 而非 csrftoken：後者匿名也拿得到，不代表已登入。
    // 純記憶體查找，不 catch 泛型例外——否則會把 cookie-store 的程式錯誤偽裝成「沒 cookie」而誤觸重載。
    public static synchronized boolean hasSessionCookie() {
        return enLcClient.getClient().cookieStore()
                .getCookie(URLUtils.getLeetcodeHost(), "LEETCODE_SESSION") != null;
    }

    public static void invalidateCache() {
        httpResponseCache.invalidateAll();
    }


    static class CacheProcessor {
        static HttpResponse processor(HttpRequest httpRequest, HttpRequestUtils.Callable<HttpResponse> callable) {

            if (!httpRequest.isCache()) {
                return callable.call(httpRequest);
            }
            try {
                // 直接用 httpRequest 當 key：它已正確實作 equals/hashCode（url+body+contentType+header+cacheParam），
                // Guava 用 equals 比對，不會像純 hashCode 字串 key 那樣發生碰撞誤命中。
                // Guava get(key, loader) 自帶 per-key 載入鎖，load-once
                return httpResponseCache.get(httpRequest, () -> {
                    HttpResponse httpResponse = callable.call(httpRequest);
                    if (httpResponse.getStatusCode() != 200) {
                        // 非 200 不寫入快取，透過例外把回應帶出去
                        throw new UncachedResponseException(httpResponse);
                    }
                    return httpResponse;
                });
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UncachedResponseException) {
                    return ((UncachedResponseException) e.getCause()).response;
                }
                throw new RuntimeException(e.getCause());
            }
        }
    }

    private static class UncachedResponseException extends Exception {
        private final HttpResponse response;

        private UncachedResponseException(HttpResponse response) {
            this.response = response;
        }
    }

    @FunctionalInterface
    interface Callable<V> {
        V call(HttpRequest request);
    }


    private static class MyExecutorHttp extends DefaultExecutoHttp {

        // lc-sdk 預設 timeout 為 connect 600s / read+write 1800s，慢連線會掛住半小時，改為合理值
        private volatile OkHttpClient directClient;
        // 平台 ProxySettings/JdkProxyProvider 底下的 ProxySelector 與 Authenticator 都是 live 的——
        // IDE 設定變更會即時反映到同一個 selector/authenticator 實例，不需要像早期改法那樣
        // 用 protocol+host+port+authFlag 組合出 cache key 來偵測設定變化再重建 client；
        // 因此 proxy client 只需一個 lazily-built 單例（原本的 per-config key 反而在 credentials
        // 單獨變更、其餘欄位不變時會產生 stale-key 命中舊 client 的漂移問題）。
        private volatile OkHttpClient proxyClient;

        private OkHttpClient getDirectClient() {
            if (directClient == null) {
                directClient = newDefaultHttpClient(10, 30, 30);
            }
            return directClient;
        }

        @Override
        public OkHttpClient getRequestClient() {
            // 無 IDE proxy 設定時直連，保住 #553（JCEF/HTTP 客戶端一致：無 proxy 不中轉）與
            // lc-sdk timeout override（直連 client 才有 newDefaultHttpClient(10,30,30)）不變式。
            if (ProxySettings.getInstance().getProxyConfiguration() instanceof ProxyConfiguration.DirectProxy) {
                return getDirectClient();
            }
            OkHttpClient client = proxyClient;
            if (client == null) {
                // proxy client 一律由 getDirectClient().newBuilder() 建，確保沿用同一組 timeout。
                client = getDirectClient().newBuilder()
                        .proxySelector(JdkProxyProvider.getInstance().getProxySelector())
                        .proxyAuthenticator(getProxyAuthenticator())
                        .build();
                proxyClient = client;
            }
            return client;
        }

        // OkHttp 對 HTTPS CONNECT 會先合成一個 scheme=OkHttp-Preemptive 的假 407 讓 authenticator 有機會
        // 搶先帶 header（真 proxy 從沒發過這個 challenge）。這個 preemptive 分支只准讀平台「已存」的憑證
        // （ProxyAuthentication.getKnownAuthentication，查無就回 null，讓請求裸送——需要認證的 proxy
        // 自會回真 407），絕不可呼叫 JdkProxyProvider.getAuthenticator()：那條路查無已知憑證時會直接彈
        // AuthenticationDialog，對「根本不需要認證的 proxy」也會無端跳窗卡住網路執行緒。
        // 真 407（route 上真的收到非 preemptive challenge）才走 JdkProxyProvider 的完整流程，彈窗才是對
        // 正當 challenge 的提示；並用 Proxy-Authorization header 是否已存在擋掉重複認證的無窮重試迴圈。
        private okhttp3.Authenticator getProxyAuthenticator() {
            return (route, response) -> {
                InetSocketAddress proxyAddress = routeProxyAddress(route);
                if (proxyAddress == null) {
                    return null;
                }

                boolean preemptive = false;
                for (Challenge challenge : response.challenges()) {
                    if (challenge.scheme().equalsIgnoreCase("OkHttp-Preemptive")) {
                        preemptive = true;
                        break;
                    }
                }

                if (preemptive) {
                    com.intellij.credentialStore.Credentials known = com.intellij.util.net.ProxyAuthentication.getInstance()
                            .getKnownAuthentication(proxyAddress.getHostString(), proxyAddress.getPort());
                    if (known == null || known.getUserName() == null || known.getPasswordAsString() == null) {
                        return null;
                    }
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", Credentials.basic(known.getUserName(), known.getPasswordAsString()))
                            .build();
                }

                // 防迴圈：已帶過 Proxy-Authorization 卻又收到 407，代表憑證無效，別再重試觸發下一輪彈窗
                if (response.request().header("Proxy-Authorization") != null) {
                    return null;
                }
                Authenticator platformAuthenticator = JdkProxyProvider.getInstance().getAuthenticator();
                PasswordAuthentication authentication = platformAuthenticator.requestPasswordAuthenticationInstance(
                        proxyAddress.getHostString(), null, proxyAddress.getPort(), "http",
                        PluginConstant.PLUGIN_ID, null, response.request().url().url(),
                        Authenticator.RequestorType.PROXY);
                if (authentication == null) {
                    return null;
                }
                return response.request().newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(authentication.getUserName(), new String(authentication.getPassword())))
                        .build();
            };
        }

        private static InetSocketAddress routeProxyAddress(Route route) {
            if (route == null) {
                return null;
            }
            SocketAddress socketAddress = route.proxy().address();
            return socketAddress instanceof InetSocketAddress ? (InetSocketAddress) socketAddress : null;
        }
    }
}

