package com.shuzijun.leetcode.plugin.model;

import java.io.File;

/**
 * 常量
 *
 * @author shuzijun
 */
public class Constant {

    /**
     * 题目难度
     */
    public static final String DIFFICULTY_EASY = "Easy";
    public static final String DIFFICULTY_MEDIUM = "Medium";
    public static final String DIFFICULTY_HARD = "Hard";

    /**
     * 树节点类型
     */
    public static final String NODETYPE_DEF = "def";

    /**
     * 类别类型
     */
    public static final String FIND_TYPE_DIFFICULTY = "Difficulty";
    public static final String FIND_TYPE_STATUS = "Status";
    public static final String FIND_TYPE_LISTS = "Lists";
    public static final String FIND_TYPE_TAGS = "Tags";
    public static final String FIND_TYPE_CATEGORY = "Category";

    /**
     * 状态类型
     */
    public static final String STATUS_TODO = "Todo";
    public static final String STATUS_SOLVED = "Solved";
    public static final String STATUS_ATTEMPTED = "Attempted";

    /**
     * 默认模板
     */
    public static final String CUSTOM_FILE_NAME = "[$!{question.frontendQuestionId}]${question.title}";
    public static final String CUSTOM_TEMPLATE = "${question.content}\n\n${question.code}";

    /**
     * 提交代码标识 submit
     */
    public static final String SUBMIT_REGION_BEGIN = "leetcode submit region begin(Prohibit modification and deletion)";
    public static final String SUBMIT_REGION_END = "leetcode submit region end(Prohibit modification and deletion)";

    /**
     * 配置文件版本记录
     */
    //第三版本，域名更新，需要将cookie更改一下域名
    public static final Integer PLUGIN_CONFIG_VERSION_3 = 3;

    /**
     * 默认题目颜色
     */
    public static final String LEVEL_COLOUR = "#5CB85C;#F0AD4E;#D9534F";

    /**
     * 文章类型
     */
    public static final Integer ARTICLE_LIVE_NONE = 0;
    public static final Integer ARTICLE_LIVE_ONE = 1;
    public static final Integer ARTICLE_LIVE_LIST = 2;

    /**
     * 排序类型
     */
    public static final int SORT_ASC = 1;
    public static final int SORT_DESC = 2;

    /**
     * 排序类别
     */
    public static final String SORT_TYPE_TITLE = "SortByTitle";
    public static final String SORT_TYPE_SOLUTION = "SortBySolution";
    public static final String SORT_TYPE_ACCEPTANCE = "SortByAcceptance";
    public static final String SORT_TYPE_DIFFICULTY = "SortByDifficulty";
    public static final String SORT_TYPE_FREQUENCY = "SortByFrequency";

    public static final String SORT_TYPE_STATES = "SortByStates";

    /**
     * path
     */
    public static final String DOC_SOLUTION = "doc"+ File.separator + "solution" +  File.separator;
    public static final String DOC_CONTENT = "doc"+ File.separator + "content" +  File.separator;
    public static final String DOC_SUBMISSION = "doc"+ File.separator + "submission" +  File.separator;
    public static final String DOC_NOTE = "doc"+ File.separator + "note" +  File.separator;


    /**
     * CodeTop类别类型
     */
    public static final String CODETOP_FIND_TYPE_COMPANY = "Company";
    /**
     * CodeTop排序类别
     */
    public static final String CODETOP_SORT_TYPE_TITLE = "CodeTopSortByTitle";
    public static final String CODETOP_SORT_TYPE_TIME = "CodeTopSortByTime";
    public static final String CODETOP_SORT_TYPE_FREQUENCY = "CodeTopSortByFrequency";


}
