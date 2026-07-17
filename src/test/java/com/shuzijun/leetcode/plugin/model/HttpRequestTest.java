package com.shuzijun.leetcode.plugin.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * HttpRequest 的值語意：HTTP 層以它當快取 key，
 * 兩個不同請求絕不能被視為同一筆快取。
 */
public class HttpRequestTest {

    @Test
    public void samePayloadIsEqual() {
        HttpRequest a = HttpRequest.builderGet("https://leetcode.com/graphql").body("{q}").cacheParam("user1").build();
        HttpRequest b = HttpRequest.builderGet("https://leetcode.com/graphql").body("{q}").cacheParam("user1").build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void differentUrlIsNotEqual() {
        HttpRequest a = HttpRequest.builderGet("https://leetcode.com/graphql").body("{q}").build();
        HttpRequest b = HttpRequest.builderGet("https://leetcode.cn/graphql").body("{q}").build();
        assertNotEquals(a, b);
    }

    @Test
    public void differentBodyIsNotEqual() {
        HttpRequest a = HttpRequest.builderGet("https://leetcode.com/graphql").body("{a}").build();
        HttpRequest b = HttpRequest.builderGet("https://leetcode.com/graphql").body("{b}").build();
        assertNotEquals(a, b);
    }

    @Test
    public void differentCacheParamIsNotEqual() {
        HttpRequest a = HttpRequest.builderGet("https://leetcode.com/graphql").cacheParam("user1").build();
        HttpRequest b = HttpRequest.builderGet("https://leetcode.com/graphql").cacheParam("user2").build();
        assertNotEquals(a, b);
    }
}
