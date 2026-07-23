package com.shuzijun.leetcode.plugin.utils;

import com.intellij.openapi.Disposable;

/**
 * Sentry client 的釋放邊界：application service 在 plugin 卸載/IDE 關閉時由平台 dispose，
 * 不像 Disposer.register(Application, lambda) 只在 IDE 關閉才跑、且讓全域 Disposer 持有 plugin classloader 的 lambda。
 * 由 SentryUtils 建立 client 時 getService() 觸發實例化。
 */
public final class SentryLifecycle implements Disposable {

    @Override
    public void dispose() {
        SentryUtils.closeClient();
    }
}
