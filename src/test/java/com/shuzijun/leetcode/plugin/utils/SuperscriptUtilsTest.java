package com.shuzijun.leetcode.plugin.utils;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SuperscriptUtils 負責把複雜度標記（如 O(n2)）轉成上標字元顯示在題目列表 UI，
 * 映射表一旦漂移，整個插件的複雜度顯示就會變成亂碼或錯字，這裡固定住轉換行為。
 */
public class SuperscriptUtilsTest {

    @Test
    public void digitsMapToSuperscript() {
        assertEquals("⁰¹²³⁴⁵⁶⁷⁸⁹",
                SuperscriptUtils.getSup("0123456789"));
    }

    @Test
    public void symbolsMapToSuperscript() {
        // '/' 表內映射為自身，'.' 映射為 U+02D9
        assertEquals("⁺⁻⁼⁽⁾˙/",
                SuperscriptUtils.getSup("+-=()./"));
    }

    @Test
    public void lowercaseLettersMapToSuperscript() {
        assertEquals("ⁿˡᵒᵍ", SuperscriptUtils.getSup("nlog"));
    }

    @Test
    public void unmappedCharsKeptAsIs() {
        // 大寫、'^'、'q'（表內無此鍵）原樣保留，混排時已映射字元照常轉換
        assertEquals("N^²", SuperscriptUtils.getSup("N^2"));
        assertEquals("q", SuperscriptUtils.getSup("q"));
    }

    @Test
    public void blankInputReturnedAsIs() {
        assertEquals("", SuperscriptUtils.getSup(""));
        assertEquals("   ", SuperscriptUtils.getSup("   "));
        assertNull(SuperscriptUtils.getSup(null));
    }
}
