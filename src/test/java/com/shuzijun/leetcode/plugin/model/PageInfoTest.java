package com.shuzijun.leetcode.plugin.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * PageInfo 的分頁數學與 filter 開關：題目列表的翻頁 offset、總頁數與
 * 篩選條件全靠它算，skip 算錯就抓錯頁、filter 狀態殘留就查錯題。
 */
public class PageInfoTest {

    @Test
    public void skipIsRowsBeforeCurrentPage() {
        PageInfo<String> page = new PageInfo<>(3, 20);
        // 第 3 頁、每頁 20 筆：前面已有 2 整頁共 40 筆
        assertEquals(40, page.getSkip());
        // 第 1 頁不跳過任何資料
        assertEquals(0, new PageInfo<String>(1, 20).getSkip());
    }

    @Test
    public void pageTotalRoundsUpRemainder() {
        PageInfo<String> page = new PageInfo<>(1, 50);
        page.setRowTotal(101);
        // 50+50 裝不下 101 筆，零頭 1 筆要多開一頁
        assertEquals(3, page.getPageTotal());
        page.setRowTotal(100);
        // 剛好整除就不多開
        assertEquals(2, page.getPageTotal());
    }

    @Test
    public void setPageSizeClampsPageIndexIntoRange() {
        PageInfo<String> page = new PageInfo<>(10, 10);
        page.setRowTotal(25);
        // 25 筆改成每頁 10 筆只有 3 頁，第 10 頁夾回最後一頁
        page.setPageSize(10);
        assertEquals(3, page.getPageIndex());
        // 沒有任何資料時退回第 1 頁
        PageInfo<String> empty = new PageInfo<>(5, 10);
        empty.setRowTotal(0);
        empty.setPageSize(10);
        assertEquals(1, empty.getPageIndex());
    }

    @Test
    public void disposeFiltersSetsAndClearsStringField() {
        PageInfo<String> page = new PageInfo<>(1, 20);
        page.disposeFilters("difficulty", "EASY", true);
        assertEquals("EASY", page.getFilters().getDifficulty());
        page.disposeFilters("difficulty", "EASY", false);
        assertNull(page.getFilters().getDifficulty());
        // 不存在的 key 靜默略過，不影響其他欄位
        page.disposeFilters("notAField", "x", true);
        assertTrue(page.isNoFilter());
    }

    @Test
    public void disposeFiltersListFieldBecomesNullWhenEmptied() {
        PageInfo<String> page = new PageInfo<>(1, 20);
        page.disposeFilters("tags", "array", true);
        page.disposeFilters("tags", "tree", true);
        assertEquals(2, page.getFilters().getTags().size());
        page.disposeFilters("tags", "array", false);
        assertEquals(1, page.getFilters().getTags().size());
        // 移除最後一個元素後整個 List 歸 null，而不是留空殼
        page.disposeFilters("tags", "tree", false);
        assertNull(page.getFilters().getTags());
    }

    @Test
    public void isNoFilterTrueOnlyWhenCategoryBlankAndFiltersEmpty() {
        PageInfo<String> page = new PageInfo<>(1, 20);
        assertTrue(page.isNoFilter());
        // 空白字串的 categorySlug 視同沒設
        page.setCategorySlug("   ");
        assertTrue(page.isNoFilter());
        page.setCategorySlug("algorithms");
        assertFalse(page.isNoFilter());
        page.setCategorySlug("");
        page.disposeFilters("status", "AC", true);
        assertFalse(page.isNoFilter());
    }

    @Test
    public void sliceOfEmptyListReturnsEmpty() {
        PageInfo<String> page = new PageInfo<>(1, 10);
        page.setRowTotal(0);
        assertTrue(page.slice(new ArrayList<>()).isEmpty());
    }

    @Test
    public void sliceReturnsAllWhenFewerRowsThanOnePage() {
        List<String> all = Arrays.asList("a", "b", "c");
        PageInfo<String> page = new PageInfo<>(1, 10);
        page.setRowTotal(all.size());
        assertEquals(Arrays.asList("a", "b", "c"), page.slice(all));
    }

    @Test
    public void sliceHandlesExactDivisionBoundary() {
        List<String> all = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            all.add("q" + i);
        }
        PageInfo<String> page = new PageInfo<>(2, 10);
        page.setRowTotal(all.size());
        // 20 筆整除成 2 頁，第 2 頁剛好裝滿 10 筆、不多不少
        List<String> page2 = page.slice(all);
        assertEquals(10, page2.size());
        assertEquals("q10", page2.get(0));
        assertEquals("q19", page2.get(9));
    }

    @Test
    public void sliceClampsOutOfRangePageIndexToLastPage() {
        List<String> all = Arrays.asList("a", "b", "c");
        // pageIndex 99 遠超總頁數，slice 要夾回最後一頁而不是丟例外
        PageInfo<String> page = new PageInfo<>(99, 10);
        page.setRowTotal(all.size());
        List<String> result = page.slice(all);
        assertEquals(1, page.getPageIndex());
        assertEquals(Arrays.asList("a", "b", "c"), result);
    }

    @Test
    public void sliceRecalculatesAfterPageSizeChange() {
        List<String> all = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            all.add("q" + i);
        }
        PageInfo<String> page = new PageInfo<>(3, 10);
        page.setRowTotal(all.size());
        // 每頁 10 筆、第 3 頁只剩 5 筆(20..24)
        assertEquals(5, page.slice(all).size());
        // 改成每頁 25 筆後，setPageSize 已把 pageIndex 夾回第 1 頁，slice 要吐出全部 25 筆
        page.setPageSize(25);
        List<String> resliced = page.slice(all);
        assertEquals(25, resliced.size());
        assertEquals("q0", resliced.get(0));
        assertEquals("q24", resliced.get(24));
    }
}
