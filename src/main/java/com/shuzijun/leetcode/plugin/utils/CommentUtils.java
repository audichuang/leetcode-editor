package com.shuzijun.leetcode.plugin.utils;

import com.shuzijun.leetcode.plugin.model.CodeTypeEnum;
import com.shuzijun.leetcode.plugin.model.Config;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author shuzijun
 */
public class CommentUtils {

    private static final Pattern subPattern = Pattern.compile("<sup>(<span.*>?)?([0-9abcdeghijklmnoprstuvwxyz\\+\\-\\*=\\(\\)\\.\\/]+)(</span>)?</sup>?");
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("(\\r\\n|\\r|\\n|\\n\\r)");

    public static String createComment(String html, CodeTypeEnum codeTypeEnum, Config config) {
        boolean isSupportMultilineComment =  config.getMultilineComment() && StringUtils.isNotBlank(codeTypeEnum.getMultiLineComment());
        html = NEWLINE_PATTERN.matcher(html).replaceAll("\\\\n").replaceAll(" "," ");
        if(config.getHtmlContent()) {
            if(isSupportMultilineComment){
                return String.format(codeTypeEnum.getMultiLineComment(),html.replace("\\n", "\n"));
            }else {
               return codeTypeEnum.getComment() + html.replace("\\n", "\n" + codeTypeEnum.getComment());
            }
        }
        Matcher subMatcher = subPattern.matcher(html);
        while (subMatcher.find()) {
            String subStr = SuperscriptUtils.getSup(subMatcher.group(2));
            html = html.replace(subMatcher.group(), "<sup>" + subStr + "</sup>");
        }
        String comment = isSupportMultilineComment?"":codeTypeEnum.getComment();
        String body = comment + Jsoup.parse(html).text().replace("\\n", "\n" + comment);
        String[] lines = body.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int c = line.length() / 80;
            if (c == 0) {
                sb.append(line).append("\n");
            } else {
                StringBuilder lineBuilder = new StringBuilder(line);
                for (int i = c; i > 0; i--) {
                    int idx = 80 * i;
                    while (idx > (80 * i - 20)) {
                        if (isSplit(lineBuilder.charAt(idx - 1))) {
                            break;
                        }
                        idx = idx - 1;
                    }
                    lineBuilder.insert(idx, "\n" + comment);
                }
                sb.append(lineBuilder).append("\n");
            }
        }
        return isSupportMultilineComment?String.format(codeTypeEnum.getMultiLineComment(),sb):sb.toString();
    }

    private static boolean isSplit(char c) {
        if (c == 34 || c == 39 || (c >= 65 && c <= 90) || (c >= 97 && c <= 122)) {
            return false;
        }
        return true;
    }
}
