package com.shuzijun.leetcode.plugin.editor;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.FileResponses;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author shuzijun
 */
public class ResourcesController extends BaseController {

    private static final Logger LOG = Logger.getInstance(ResourcesController.class);

    // ponytail: 只有 vditor 這個子樹會被 default.html/method.min.js 動態引用（css/js/字型/主題/emoji…），
    // 其餘路徑一律 404，避免 request path 直接被當 classpath resource 名對任意資源放行；
    // 上限用 byte[] 長度算 weight（沿用 HttpRequestUtils 的 Guava maximumWeight 寫法），16MB 對 vditor 全量資源綽綽有餘
    private static final String ALLOWED_PREFIX = "/vditor/";

    private static final Cache<String, byte[]> resourceCache = CacheBuilder.newBuilder()
            .maximumWeight(16L * 1024 * 1024)
            .weigher((String k, byte[] v) -> v.length)
            .build();

    private final String controllerPath = "resources";

    @Override
    public String getControllerPath() {
        return controllerPath;
    }

    @Override
    public FullHttpResponse get(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) {
        String resourceName = getResourceName(urlDecoder);
        if (!resourceName.startsWith(ALLOWED_PREFIX) || resourceName.contains("..")) {
            return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, Unpooled.EMPTY_BUFFER);
        }
        byte[] data = resourceCache.getIfPresent(resourceName);
        if (data == null) {
            try (InputStream inputStream = PreviewStaticServer.class.getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, Unpooled.EMPTY_BUFFER);
                }
                data = FileUtilRt.loadBytes(inputStream);
                resourceCache.put(resourceName, data);
            } catch (IOException e) {
                LOG.warn(e);
                return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.EMPTY_BUFFER);
            }
        }

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(data));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, FileResponses.INSTANCE.getContentType(resourceName) + "; charset=utf-8");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=3600, public");
        response.headers().set(HttpHeaderNames.ETAG, Long.toString(LAST_MODIFIED));
        return response;
    }
}
