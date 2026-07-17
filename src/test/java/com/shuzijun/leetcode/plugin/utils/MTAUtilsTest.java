package com.shuzijun.leetcode.plugin.utils;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * getI 產生設定檔用的匿名 id（追蹤 beacon 已移除，僅保留 id 產生器）。
 */
public class MTAUtilsTest {

    @Test
    public void idStartsWithPrefixAndIsNumeric() {
        String id = MTAUtils.getI("u-");
        assertTrue(id.startsWith("u-"));
        assertTrue(id.substring(2).matches("\\d+"));
    }

    @Test
    public void emptyPrefixStillProducesId() {
        String id = MTAUtils.getI("");
        assertFalse(id.isEmpty());
        assertTrue(id.matches("\\d+"));
    }
}
