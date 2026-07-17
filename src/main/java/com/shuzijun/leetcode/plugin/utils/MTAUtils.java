package com.shuzijun.leetcode.plugin.utils;

/**
 * @author shuzijun
 */
public class MTAUtils {

    public static String getI(String prefix) {
        int[] b = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int e = 10; 1 < e; e--) {
            int c = (int) Math.floor(10 * Math.random());
            int f = b[c];
            b[c] = b[e - 1];
            b[e - 1] = f;
        }
        int c = 0;
        for (int e = 0; 5 > e; e++) {
            c = 10 * c + b[e];
        }
        return prefix + c + System.currentTimeMillis();
    }
}
