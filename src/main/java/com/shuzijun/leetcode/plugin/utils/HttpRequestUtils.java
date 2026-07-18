package com.shuzijun.leetcode.plugin.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.IdeaWideProxySelector;
import com.intellij.util.proxy.NonStaticAuthenticator;
import com.shuzijun.lc.LcClient;
import com.shuzijun.lc.errors.LcException;
import com.shuzijun.lc.http.DefaultExecutoHttp;
import com.shuzijun.lc.http.HttpClient;
import com.shuzijun.leetcode.plugin.model.HttpRequest;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import okhttp3.Challenge;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.net.HttpCookie;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author shuzijun
 */
public class HttpRequestUtils {

    // ponytail: 上限單位從「筆數」改成「位元組」，避免少數大 body（如 allQuestions ~2MB）把 200 個 slot 撐成數百MB；
    // weigher 用 UTF-8 位元組數（而非 String.length() 的 UTF-16 字元數），CJK body 才不會被低估近半；
    // 其餘 LRU/TTL/per-key 載入鎖語意不變，只是淘汰依據換了計量單位
    private static final Cache<HttpRequest, HttpResponse> httpResponseCache = CacheBuilder.newBuilder()
            .maximumWeight(32L * 1024 * 1024)
            .weigher((HttpRequest k, HttpResponse v) -> v.getBody() == null ? 0 : v.getBody().getBytes(StandardCharsets.UTF_8).length)
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

    public static boolean isLogin(Project project) {
        HttpResponse response = HttpRequest.builderGet(URLUtils.getLeetcodePoints()).request();
        if (response.getStatusCode() == 200) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    public static void setCookie(List<HttpCookie> cookieList) {
        enLcClient.getClient().cookieStore().clearCookie(URLUtils.getLeetcodeHost());
        enLcClient.getClient().cookieStore().addCookie(URLUtils.getLeetcodeHost(), cookieList);
    }

    public static void resetHttpclient() {
        enLcClient.getClient().cookieStore().clearCookie(URLUtils.getLeetcodeHost());
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
        // key 與 client 合併成單一 immutable holder，用單一 volatile 欄位原子發布，
        // 避免並行請求讀到「client B + key A」的撕裂狀態而命中沒有正確 authenticator 的 client
        private volatile ProxyHolder proxyHolder;

        private static final class ProxyHolder {
            private final String key;
            private final OkHttpClient client;

            private ProxyHolder(String key, OkHttpClient client) {
                this.key = key;
                this.client = client;
            }
        }

        private OkHttpClient getDirectClient() {
            if (directClient == null) {
                directClient = newDefaultHttpClient(10, 30, 30);
            }
            return directClient;
        }

        @Override
        public OkHttpClient getRequestClient() {
            final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
            if (!httpConfigurable.USE_HTTP_PROXY && !httpConfigurable.USE_PROXY_PAC) {
                return getDirectClient();
            }
            String key = httpConfigurable.USE_HTTP_PROXY + "|" + httpConfigurable.USE_PROXY_PAC + "|" + httpConfigurable.PROXY_AUTHENTICATION
                    + "|" + httpConfigurable.PROXY_HOST + ":" + httpConfigurable.PROXY_PORT;
            ProxyHolder holder = proxyHolder;
            if (holder != null && key.equals(holder.key)) {
                return holder.client;
            }
            final IdeaWideProxySelector ideaWideProxySelector = new IdeaWideProxySelector(httpConfigurable);
            OkHttpClient.Builder builder = getDirectClient().newBuilder().proxySelector(ideaWideProxySelector);
            if (httpConfigurable.PROXY_AUTHENTICATION) {
                final MyAuthenticator ideaWideAuthenticator = new MyAuthenticator(httpConfigurable);
                final okhttp3.Authenticator proxyAuthenticator = getProxyAuthenticator(ideaWideAuthenticator);
                builder.proxyAuthenticator(proxyAuthenticator);
            }
            OkHttpClient built = builder.build();
            proxyHolder = new ProxyHolder(key, built);
            return built;
        }

        private okhttp3.Authenticator getProxyAuthenticator(MyAuthenticator ideaWideAuthenticator) {
            okhttp3.Authenticator proxyAuthenticator = null;

            if (Objects.nonNull(ideaWideAuthenticator)) {
                proxyAuthenticator = (route, response) -> {
                    ideaWideAuthenticator.SetResponse(response);
                    final PasswordAuthentication authentication = ideaWideAuthenticator.getPasswordAuthentication();
                    final String credential = Credentials.basic(authentication.getUserName(), new String(authentication.getPassword()));

                    for (Challenge challenge : response.challenges()) {
                        if (challenge.scheme().equalsIgnoreCase("OkHttp-Preemptive")) {
                            return response.request().newBuilder()
                                    .header("Proxy-Authorization", credential)
                                    .build();
                        }
                    }
                    return null;
                };
            }
            return proxyAuthenticator;
        }
    }

    private static class MyAuthenticator extends NonStaticAuthenticator {
        private static final Logger LOG = Logger.getInstance(com.intellij.util.net.IdeaWideAuthenticator.class);
        private final HttpConfigurable myHttpConfigurable;

        private okhttp3.Response response;

        public MyAuthenticator(@NotNull HttpConfigurable configurable) {
            super();
            this.myHttpConfigurable = configurable;
        }


        public void SetResponse(okhttp3.Response response) {
            this.response = response;
        }

        public PasswordAuthentication getPasswordAuthentication() {
            okhttp3.HttpUrl url = response.request().url();
            Application application = ApplicationManager.getApplication();

            if (StringUtils.isNoneBlank(myHttpConfigurable.getPlainProxyPassword()) && StringUtils.isNoneBlank(myHttpConfigurable.getProxyLogin())) {
                return new PasswordAuthentication(myHttpConfigurable.getProxyLogin(), myHttpConfigurable.getPlainProxyPassword().toCharArray());
            }

            if (this.myHttpConfigurable.USE_HTTP_PROXY) {
                LOG.debug("CommonAuthenticator.getPasswordAuthentication will return common defined proxy");
                return this.myHttpConfigurable.getPromptedAuthentication(url.host() + ":" + url.port(), this.getRequestingPrompt());
            }

            if (this.myHttpConfigurable.USE_PROXY_PAC) {
                LOG.debug("CommonAuthenticator.getPasswordAuthentication will return autodetected proxy");
                if (this.myHttpConfigurable.isGenericPasswordCanceled(this.getRequestingHost(), this.getRequestingPort())) {
                    return null;
                }

                PasswordAuthentication password = this.myHttpConfigurable.getGenericPassword(this.getRequestingHost(), this.getRequestingPort());
                if (password != null) {
                    return password;
                }

                if (application != null && !application.isDisposed()) {
                    return this.myHttpConfigurable.getGenericPromptedAuthentication(PluginConstant.PLUGIN_ID, this.getRequestingHost(), this.getRequestingPrompt(), this.getRequestingPort(), true);
                }

                return null;
            }

            if (application != null && !application.isDisposed()) {
                LOG.debug("CommonAuthenticator.getPasswordAuthentication generic authentication will be asked");
                return null;
            } else {
                return null;
            }
        }

        @Override
        protected String getRequestingHost() {
            return response.request().url().host();
        }

        @Override
        protected int getRequestingPort() {
            return response.request().url().port();
        }

        @Override
        protected @Nls String getRequestingPrompt() {
            return PluginConstant.PLUGIN_ID;
        }
    }
}

