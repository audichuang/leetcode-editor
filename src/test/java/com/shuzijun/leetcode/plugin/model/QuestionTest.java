package com.shuzijun.leetcode.plugin.model;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Question.getCode 是「遠端題目資料 → 本地程式碼檔內容」的 seam：
 * 產出的 submit region 標記之後會被解析器切回提交用程式碼，
 * 標記格式或註解符號一旦跑掉，提交流程就會整段壞掉。
 */
public class QuestionTest {

    private static final String BEGIN = "leetcode submit region begin(Prohibit modification and deletion)";
    private static final String END = "leetcode submit region end(Prohibit modification and deletion)";

    private static CodeSnippet snippet(String langSlug, String code) {
        CodeSnippet snippet = new CodeSnippet();
        snippet.setLangSlug(langSlug);
        snippet.setCode(code);
        return snippet;
    }

    @Test
    public void nullSnippetsMeansLocked() {
        Question question = new Question("Two Sum");
        question.setLangSlug("java");
        assertEquals("Subscribe to unlock.", question.getCode());
    }

    @Test
    public void emptySnippetsMeansLocked() {
        Question question = new Question("Two Sum");
        question.setLangSlug("java");
        question.setCodeSnippets(Collections.emptyList());
        assertEquals("Subscribe to unlock.", question.getCode());
    }

    @Test
    public void matchedJavaSnippetIsWrappedWithSlashComments() {
        Question question = new Question("Two Sum");
        question.setLangSlug("java");
        question.setCodeSnippets(Collections.singletonList(snippet("java", "class Solution {}")));
        assertEquals("//" + BEGIN + "\n"
                + "class Solution {}\n"
                + "//" + END + "\n", question.getCode());
    }

    @Test
    public void matchedPythonSnippetUsesHashComments() {
        Question question = new Question("Two Sum");
        question.setLangSlug("python");
        question.setCodeSnippets(Collections.singletonList(snippet("python", "class Solution:")));
        assertEquals("# " + BEGIN + "\n"
                + "class Solution:\n"
                + "# " + END + "\n", question.getCode());
    }

    @Test
    public void langSlugLookupIsCaseInsensitive() {
        Question question = new Question("Two Sum");
        question.setLangSlug("JAVA");
        question.setCodeSnippets(Collections.singletonList(snippet("java", "class Solution {}")));
        assertEquals("//" + BEGIN + "\n"
                + "class Solution {}\n"
                + "//" + END + "\n", question.getCode());
    }

    @Test
    public void missingLanguageSnippetReturnsNoCodeHint() {
        Question question = new Question("Two Sum");
        question.setLangSlug("java");
        question.setCodeSnippets(Collections.singletonList(snippet("python", "class Solution:")));
        assertEquals("//There is no code of Java type for this problem", question.getCode());
    }
}
