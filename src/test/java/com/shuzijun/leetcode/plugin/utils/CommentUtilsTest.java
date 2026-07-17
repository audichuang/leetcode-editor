package com.shuzijun.leetcode.plugin.utils;

import com.shuzijun.leetcode.plugin.model.CodeTypeEnum;
import com.shuzijun.leetcode.plugin.model.Config;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * CommentUtils 是「題目 HTML → 程式碼註解」的純字串轉換 seam：
 * 每個新開的題目檔案頂部註解都經過它，且不依賴 IntelliJ 平台執行期，
 * 一旦回歸會默默弄壞所有新產生的解題檔，因此值得headless鎖定行為。
 */
public class CommentUtilsTest {

    @Test
    public void stripsTagsAndPrefixesEachLine() {
        Config config = new Config();
        // 標籤剝除，單行加 // 前綴
        assertEquals("//Two Sum\n",
                CommentUtils.createComment("<div>Two Sum</div>", CodeTypeEnum.JAVA, config));
        // 換行的每一行都各自加前綴
        assertEquals("//first\n//second\n",
                CommentUtils.createComment("first\nsecond", CodeTypeEnum.JAVA, config));
        // 邊界：空輸入仍給出一行前綴
        assertEquals("//\n",
                CommentUtils.createComment("", CodeTypeEnum.JAVA, config));
    }

    @Test
    public void wrapsLongLineAtLastSpaceWithin80Columns() {
        // 20 個 "word" 以空白相隔：前綴後共 2+99=101 字元，超過 80。
        // 規格：在第 61~80 欄間最後一個標點/空白處回折；
        // 最後一個落在 80 欄內的空白是第 15 個字後面（欄 77），故在此折行。
        String text = String.join(" ", java.util.Collections.nCopies(20, "word"));
        String expected = "//" + "word ".repeat(15) + "\n"
                + "//" + "word ".repeat(4) + "word" + "\n";
        String actual = CommentUtils.createComment(text, CodeTypeEnum.JAVA, new Config());
        assertEquals(expected, actual);
        for (String line : actual.split("\n")) {
            assertTrue("line exceeds 80 columns: " + line, line.length() <= 80);
        }
    }

    @Test
    public void convertsSupTagToUnicodeSuperscript() {
        assertEquals("//O(n²)\n",
                CommentUtils.createComment("<p>O(n<sup>2</sup>)</p>", CodeTypeEnum.JAVA, new Config()));
    }

    @Test
    public void htmlContentKeepsTagsWithSingleLineCommentPrefix() {
        Config config = new Config();
        config.setHtmlContent(true);
        assertEquals("//<p>a</p>\n//<p>b</p>",
                CommentUtils.createComment("<p>a</p>\n<p>b</p>", CodeTypeEnum.JAVA, config));
    }

    @Test
    public void multilineCommentWrapsBodyInBlockComment() {
        Config config = new Config();
        config.setMultilineComment(true);
        // 行內不加 // 前綴，整段包進 JAVA 的 /** ... */；處理後的內容自帶行尾換行
        assertEquals("/**\nTwo Sum\n\n*/",
                CommentUtils.createComment("Two Sum", CodeTypeEnum.JAVA, config));
    }

    @Test
    public void createSubmissionsExtractsPageDataAndStripsStatusCode() {
        String page = "<script>\n"
                + "var pageData = {\n"
                + "  submissionData: {\n"
                + "    status_code: parseInt('10', 10),\n"
                + "    runtime: '12 ms'\n"
                + "  }\n"
                + "};\n"
                + "if (isNaN(pageData.submissionData.status_code)) { pageData.submissionData.status_code = 12; }\n"
                + "</script>";
        String result = CommentUtils.createSubmissions(page);
        assertFalse(result.contains("status_code"));
        assertTrue(result.contains("runtime: '12 ms'"));
        assertFalse(result.contains("\n"));
        assertFalse(result.contains("if (isNaN"));
        // 邊界：頁面沒有 pageData 片段時回傳 null
        assertNull(CommentUtils.createSubmissions("<html>no page data</html>"));
    }
}
