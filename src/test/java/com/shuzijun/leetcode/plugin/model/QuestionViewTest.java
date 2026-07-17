package com.shuzijun.leetcode.plugin.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * QuestionView 是題目列表排序與顯示的核心模型：
 * frontendQuestionIdCompareTo 決定側邊欄題目的順序，
 * 純數字編號必須以數值序排列（"2" 在 "10" 前），
 * 「剑指 Offer」「面试题」等特殊題集必須整組排在數字題之後且剑在面前；
 * setLevel / setAcceptance 則是伺服器回傳字串到內部值的唯一轉換點。
 */
public class QuestionViewTest {

    private static QuestionView withId(String frontendQuestionId) {
        QuestionView view = new QuestionView();
        view.setFrontendQuestionId(frontendQuestionId);
        return view;
    }

    @Test
    public void numericIdsCompareByLengthThenLexicographic() {
        assertTrue(withId("2").frontendQuestionIdCompareTo(withId("10")) < 0);
        assertTrue(withId("10").frontendQuestionIdCompareTo(withId("2")) > 0);
        assertTrue(withId("10").frontendQuestionIdCompareTo(withId("11")) < 0);
        assertTrue(withId("999").frontendQuestionIdCompareTo(withId("1000")) < 0);
        assertEquals(0, withId("42").frontendQuestionIdCompareTo(withId("42")));
    }

    @Test
    public void specialPrefixIdsSortAfterNumericIds() {
        assertTrue(withId("剑指 Offer 03").frontendQuestionIdCompareTo(withId("9999")) > 0);
        assertTrue(withId("9999").frontendQuestionIdCompareTo(withId("剑指 Offer 03")) < 0);
        assertTrue(withId("面试题 01.01").frontendQuestionIdCompareTo(withId("1")) > 0);
    }

    @Test
    public void jianSortsBeforeMianAndSamePrefixIsLexicographic() {
        assertTrue(withId("剑指 Offer 03").frontendQuestionIdCompareTo(withId("面试题 01.01")) < 0);
        assertTrue(withId("面试题 01.01").frontendQuestionIdCompareTo(withId("剑指 Offer 03")) > 0);
        assertTrue(withId("剑指 Offer 03").frontendQuestionIdCompareTo(withId("剑指 Offer 10")) < 0);
        assertEquals(0, withId("面试题 01.01").frontendQuestionIdCompareTo(withId("面试题 01.01")));
    }

    @Test
    public void setLevelMapsDifficultyStrings() {
        QuestionView view = new QuestionView();
        view.setLevel("easy");
        assertEquals(Integer.valueOf(1), view.getLevel());
        view.setLevel("Medium");
        assertEquals(Integer.valueOf(2), view.getLevel());
        view.setLevel("HARD");
        assertEquals(Integer.valueOf(3), view.getLevel());
        view.setLevel("2");
        assertEquals(Integer.valueOf(2), view.getLevel());
    }

    @Test
    public void setLevelFallsBackToZeroOnNullOrUnknown() {
        QuestionView view = new QuestionView();
        view.setLevel(null);
        assertEquals(Integer.valueOf(0), view.getLevel());
        view.setLevel("unknown");
        assertEquals(Integer.valueOf(0), view.getLevel());
        view.setLevel("0");
        assertEquals(Integer.valueOf(0), view.getLevel());
        view.setLevel("4");
        assertEquals(Integer.valueOf(0), view.getLevel());
    }

    @Test
    public void setAcceptanceNormalizesPercentValues() {
        QuestionView view = new QuestionView();
        view.setAcceptance(45.6D);
        assertEquals(0.456D, view.getAcceptance(), 1e-9);
        view.setAcceptance(0.5D);
        assertEquals(0.5D, view.getAcceptance(), 1e-9);
        view.setAcceptance(1.0D);
        assertEquals(1.0D, view.getAcceptance(), 1e-9);
        view.setAcceptance(0D);
        assertEquals(0D, view.getAcceptance(), 1e-9);
        view.setAcceptance(null);
        assertNull(view.getAcceptance());
    }
}
