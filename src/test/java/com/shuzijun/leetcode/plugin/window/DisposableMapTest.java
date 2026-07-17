package com.shuzijun.leetcode.plugin.window;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * getOtherKey 是雙面板共享 user 快取的關鍵：
 * 必須回傳「別的面板」的 key，而不是自己的。
 * （8.16 以前的實作誤回傳自己的 key，導致快取共享永遠失效、多打網路。）
 */
public class DisposableMapTest {

    @Test
    public void returnsTheOtherKeyWhenPresent() {
        NavigatorTabsPanel.DisposableMap<String, Object> map = new NavigatorTabsPanel.DisposableMap<>();
        map.put("panelA", new Object());
        map.put("panelB", new Object());
        assertEquals("panelB", map.getOtherKey("panelA"));
        assertEquals("panelA", map.getOtherKey("panelB"));
    }

    @Test
    public void returnsNullWhenNoOtherKey() {
        NavigatorTabsPanel.DisposableMap<String, Object> map = new NavigatorTabsPanel.DisposableMap<>();
        map.put("panelA", new Object());
        assertNull(map.getOtherKey("panelA"));
    }

    @Test
    public void returnsNullWhenEmpty() {
        NavigatorTabsPanel.DisposableMap<String, Object> map = new NavigatorTabsPanel.DisposableMap<>();
        assertNull(map.getOtherKey("panelA"));
    }
}
