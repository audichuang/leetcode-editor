package com.shuzijun.leetcode.plugin.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Find 是題目列表篩選/排序狀態的唯一儲存點：
 * key 一律以小寫正規化，clearFilter 與 operationType 負責狀態重設，
 * resetFilterData 在重新載入清單時以 slug 搬移舊選取狀態。
 * 任何一環壞掉，UI 的篩選/排序會靜默失效。
 */
public class FindTest {

    private Tag tag(String slug, boolean select) {
        Tag tag = new Tag();
        tag.setSlug(slug);
        tag.setSelect(select);
        return tag;
    }

    @Test
    public void filterKeyIsCaseInsensitive() {
        Find find = new Find();
        List<Tag> tags = new ArrayList<>(Arrays.asList(tag("array", false)));
        find.addFilter("Difficulty", tags);
        assertSame(tags, find.getFilter("difficulty"));
        assertSame(tags, find.getFilter("DIFFICULTY"));
        assertNull(find.getFilter(null));
        assertNull(find.getFilter("unknown"));
    }

    @Test
    public void clearFilterDeselectsAllTagsAcrossKeys() {
        Find find = new Find();
        Tag a = tag("array", true);
        Tag b = tag("string", true);
        Tag c = tag("easy", false);
        find.addFilter("tags", new ArrayList<>(Arrays.asList(a, b)));
        find.addFilter("difficulty", new ArrayList<>(Arrays.asList(c)));

        find.clearFilter();

        assertFalse(a.isSelect());
        assertFalse(b.isSelect());
        assertFalse(c.isSelect());
    }

    @Test
    public void operationTypeIncrementsTargetAndResetsOthers() {
        Find find = new Find();
        Sort target = new Sort("ac_rate", 0);
        Sort other = new Sort("frontend_id", 2);
        find.addSort("AC_RATE", target);
        find.addSort("frontend_id", other);

        find.operationType("ac_rate");

        assertEquals(1, target.getType());
        assertEquals(0, other.getType());
    }

    @Test
    public void operationTypeCyclesBackToZeroAfterThreeSteps() {
        Find find = new Find();
        Sort sort = new Sort("ac_rate", 0);
        find.addSort("ac_rate", sort);

        find.operationType("ac_rate");
        find.operationType("ac_rate");
        assertEquals(2, sort.getType());

        find.operationType("ac_rate");
        assertEquals(0, sort.getType());
    }

    @Test
    public void resetFilterDataCarriesSelectionBySlug() {
        Find find = new Find();
        find.addFilter("tags", new ArrayList<>(Arrays.asList(tag("array", true), tag("string", false))));

        Tag newArray = tag("array", false);
        Tag newHash = tag("hash-table", false);
        List<Tag> newTags = new ArrayList<>(Arrays.asList(newArray, newHash));
        find.resetFilterData("tags", newTags);

        assertSame(newTags, find.getFilter("tags"));
        assertTrue(newArray.isSelect());
        assertFalse(newHash.isSelect());
    }

    @Test
    public void resetFilterDataIgnoresUnregisteredOrEmptyKey() {
        Find find = new Find();
        find.addFilter("empty", new ArrayList<>());
        List<Tag> newTags = new ArrayList<>(Arrays.asList(tag("array", false)));

        find.resetFilterData("unknown", newTags);
        find.resetFilterData("empty", newTags);

        assertNull(find.getFilter("unknown"));
        assertTrue(find.getFilter("empty").isEmpty());
    }
}
