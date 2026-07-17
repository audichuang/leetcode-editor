package com.shuzijun.leetcode.plugin.utils;

import com.shuzijun.leetcode.plugin.model.HttpRequest;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * CacheProcessor 是 HTTP 快取核心：cache=false 一律直接呼叫 loader；
 * cache=true 用 Guava get(key, loader) 保證 load-once；非 200 不寫入快取；
 * invalidateCache() 清空全部；HttpRequest 當 key 靠 equals 防止碰撞。
 * httpResponseCache 是 static，每個測試前先清空避免互相污染。
 */
public class HttpRequestUtilsCacheTest {

    @Before
    public void clearCache() {
        HttpRequestUtils.invalidateCache();
    }

    private static HttpResponse okResponse(String body) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(200);
        response.setBody(body);
        return response;
    }

    private static HttpResponse errorResponse(int statusCode) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(statusCode);
        response.setBody("error");
        return response;
    }

    @Test
    public void cacheFalse_alwaysCallsLoader() {
        HttpRequest request = HttpRequest.builderGet("https://leetcode.com/a").build();
        AtomicInteger calls = new AtomicInteger();
        HttpRequestUtils.Callable<HttpResponse> loader = req -> {
            calls.incrementAndGet();
            return okResponse("body");
        };

        HttpRequestUtils.CacheProcessor.processor(request, loader);
        HttpRequestUtils.CacheProcessor.processor(request, loader);

        assertEquals(2, calls.get());
    }

    @Test
    public void cacheTrue_loadOnce() {
        // 兩次呼叫 builder 得到兩個不同 instance，守住「Guava 用 equals 比對、
        // 跨實例仍命中快取」——同一 instance 的測試連 identity 相等都能矇混過去。
        HttpRequest request1 = HttpRequest.builderGet("https://leetcode.com/b").cacheParam("u").build();
        HttpRequest request2 = HttpRequest.builderGet("https://leetcode.com/b").cacheParam("u").build();
        assertNotSame(request1, request2);
        assertEquals(request1, request2);

        AtomicInteger calls = new AtomicInteger();
        HttpRequestUtils.Callable<HttpResponse> loader = req -> {
            calls.incrementAndGet();
            return okResponse("cached-body");
        };

        HttpResponse first = HttpRequestUtils.CacheProcessor.processor(request1, loader);
        HttpResponse second = HttpRequestUtils.CacheProcessor.processor(request2, loader);

        assertEquals(1, calls.get());
        assertEquals("cached-body", first.getBody());
        assertEquals("cached-body", second.getBody());
    }

    @Test
    public void non200_notCached() {
        HttpRequest request = HttpRequest.builderGet("https://leetcode.com/c").cacheParam("u").build();
        AtomicInteger calls = new AtomicInteger();
        HttpRequestUtils.Callable<HttpResponse> loader = req -> {
            calls.incrementAndGet();
            return errorResponse(500);
        };

        HttpResponse first = HttpRequestUtils.CacheProcessor.processor(request, loader);
        HttpResponse second = HttpRequestUtils.CacheProcessor.processor(request, loader);

        assertEquals(2, calls.get());
        // 非 200 不能寫入快取，但 ExecutionException 拆解出來的仍要是 loader 當次的原始 response，
        // 不是被吞掉變成 null 或快取裡的舊值。
        assertEquals(500, first.getStatusCode());
        assertEquals("error", first.getBody());
        assertEquals(500, second.getStatusCode());
        assertEquals("error", second.getBody());
    }

    @Test
    public void invalidate_reloads() {
        HttpRequest request = HttpRequest.builderGet("https://leetcode.com/d").cacheParam("u").build();
        AtomicInteger calls = new AtomicInteger();
        HttpRequestUtils.Callable<HttpResponse> loader = req -> {
            calls.incrementAndGet();
            return okResponse("body");
        };

        HttpRequestUtils.CacheProcessor.processor(request, loader);
        assertEquals(1, calls.get());

        HttpRequestUtils.invalidateCache();

        HttpRequestUtils.CacheProcessor.processor(request, loader);
        assertEquals(2, calls.get());
    }

    @Test
    public void collidingHashCode_noCrossHit() {
        // "Aa" 與 "BB" 是經典 String.hashCode() 碰撞（都是 2112）。HttpRequest.hashCode() 用
        // HashCodeBuilder 依序疊加 url/body/contentType/header/cacheParam，乘數 37 對 2^32 可逆，
        // 所以在其餘欄位都相同時，兩個 HttpRequest 的 hashCode() 相等 <=> body 的 hashCode() 相等——
        // 也就是說 reqA/reqB 必定 hashCode 碰撞，但 equals() 為 false（body 不同）。
        // 這是用來抓「cache key 退化成 url+"#"+hashCode() 字串」這種回歸的 tracer bullet：
        // 若真退化成那樣，reqB 會用字串誤命中 reqA 的快取項，錯拿到 "AAA"。
        String sameUrl = "https://leetcode.com/e";
        HttpRequest reqA = HttpRequest.builderGet(sameUrl).body("Aa").cacheParam("u").build();
        HttpRequest reqB = HttpRequest.builderGet(sameUrl).body("BB").cacheParam("u").build();

        // 前提斷言：先證明這確實是碰撞案例。若日後 hashCode() 實作改變導致不再碰撞，
        // 這裡會先紅燈提醒此測試已經抓不到原本想測的東西了。
        assertEquals(reqA.hashCode(), reqB.hashCode());
        assertNotEquals(reqA, reqB);

        HttpRequestUtils.Callable<HttpResponse> loaderA = req -> okResponse("AAA");
        HttpRequestUtils.Callable<HttpResponse> loaderB = req -> okResponse("BBB");

        HttpRequestUtils.CacheProcessor.processor(reqA, loaderA);
        HttpResponse resultB = HttpRequestUtils.CacheProcessor.processor(reqB, loaderB);

        assertEquals("BBB", resultB.getBody());
    }

    @Test
    public void cacheParamNull_staysUncached() {
        // 守 #763 根因：Graphql.request() 對所有請求無條件呼叫 cacheParam()，
        // 傳 null 必須是「不開快取」的 no-op，若回退成無條件 this.cache = true，
        // userStatus/randomQuestion 等即時性請求會被誤快取而拿到過期回應。
        HttpRequest uncached = HttpRequest.builderGet("https://leetcode.com/f").cacheParam(null).build();
        HttpRequest cached = HttpRequest.builderGet("https://leetcode.com/f").cacheParam("u").build();

        assertFalse(uncached.isCache());
        assertTrue(cached.isCache());
    }
}
