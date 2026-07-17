package com.shuzijun.leetcode.plugin.model;

import com.shuzijun.leetcode.plugin.utils.HttpRequestUtils;
import com.shuzijun.leetcode.plugin.utils.HttpResponse;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author shuzijun
 */
public class HttpRequest {

    private String url;

    private String body;
    /**
     * POST
     */
    private String contentType;

    private Map<String, String> header;

    private boolean cache;

    private String cacheParam;

    private final Type type;

    private HttpRequest(String url, String body, String contentType, Map<String, String> header, boolean cache, String cacheParam, Type type) {
        this.url = url;
        this.body = body;
        this.contentType = contentType;
        // defensive copy: builder 的 header map 是 mutable 且已交給呼叫端(getHeader())，
        // build() 後若原 map 被改動會讓已入快取的 key hash 跟著漂移
        this.header = new HashMap<>(header);
        this.cache = cache;
        this.cacheParam = cacheParam;
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public String getBody() {
        return body;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, String> getHeader() {
        // 不可變視圖：防止呼叫端拿到 map 後修改，動到已入快取的 key hash
        return Collections.unmodifiableMap(header);
    }

    public boolean isCache() {
        return cache;
    }

    public String getCacheParam() {
        return cacheParam;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        HttpRequest that = (HttpRequest) o;

        // method 納入 cache key：否則同 url+body 的 GET 與 POST 會被誤判成同一筆快取
        return new EqualsBuilder().append(url, that.url).append(body, that.body).append(contentType, that.contentType).append(header, that.header).append(cacheParam, that.cacheParam).append(type, that.type).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(url).append(body).append(contentType).append(header).append(cacheParam).append(type).toHashCode();
    }

    public static HttpRequest.HttpRequestBuilder builderGet(String url) {
        return new HttpRequest.HttpRequestBuilder().get(url);
    }

    public static HttpRequest.HttpRequestBuilder builderPost(String url, String contentType) {
        return new HttpRequest.HttpRequestBuilder().post(url, contentType);
    }

    public static HttpRequest.HttpRequestBuilder builderPut(String url, String contentType) {
        return new HttpRequest.HttpRequestBuilder().put(url, contentType);
    }

    public static class HttpRequestBuilder {
        private String url;

        private String body;
        /**
         * POST
         */
        private String contentType;

        private Type type;

        private Map<String, String> header = new HashMap<>();

        private boolean cache = false;

        private String cacheParam;

        private HttpRequestBuilder() {

        }

        private HttpRequestBuilder get(String url) {
            this.url = url;
            this.type = Type.GET;
            return this;
        }

        private HttpRequestBuilder post(String url, String contentType) {
            this.url = url;
            this.contentType = contentType;
            this.type = Type.POST;
            return this;
        }

        private HttpRequestBuilder put(String url, String contentType) {
            this.url = url;
            this.contentType = contentType;
            this.type = Type.PUT;
            return this;
        }

        public HttpRequestBuilder body(String body) {
            this.body = body;
            return this;
        }

        public HttpRequestBuilder addHeader(String name, String value) {
            this.header.put(name, value);
            return this;
        }

        public HttpRequestBuilder cache(boolean cache) {
            this.cache = cache;
            return this;
        }

        public HttpRequestBuilder cacheParam(String cacheParam) {
            // null 表示未選擇快取：Graphql.request() 對所有請求都會呼叫本方法，
            // 無條件開快取會讓 userStatus/randomQuestion 等即時性請求吃到過期回應（上游 #763 根因）
            if (cacheParam != null) {
                this.cacheParam = cacheParam;
                this.cache = true;
            }
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(url, body, contentType, header, cache, cacheParam, type);
        }

        @NotNull
        public HttpResponse request() {
            HttpRequest httpRequest = build();
            switch (type) {
                case GET:
                    return HttpRequestUtils.executeGet(httpRequest);
                case POST:
                    return HttpRequestUtils.executePost(httpRequest);
                case PUT:
                    return HttpRequestUtils.executePut(httpRequest);
                default:
                    throw new RuntimeException("Type not supported");
            }

        }

    }

    private enum Type {
        GET, POST, PUT;
    }
}