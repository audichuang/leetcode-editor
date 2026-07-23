package com.shuzijun.leetcode.plugin.editor;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.Responses;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author shuzijun
 */
public abstract class BaseController {

    // every time the plugin starts up, assume resources could have been modified
    protected static final long LAST_MODIFIED = System.currentTimeMillis();

    public final void process(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
        FullHttpResponse response;
        try {
            if (request.method() == HttpMethod.POST) {
                response = post(urlDecoder, request, context);
            } else if (request.method() == HttpMethod.GET) {
                response = get(urlDecoder, request, context);
            } else {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            }
        } catch (Throwable t) {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.wrappedBuffer(t.getMessage().getBytes(StandardCharsets.UTF_8)));
        }
        Responses.send(response, context.channel(), request);
        if (response.content() != Unpooled.EMPTY_BUFFER) {
            try {
                response.release();
            } catch (Exception ignore) {
            }
        }
    }

    public FullHttpResponse get(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
    }

    public FullHttpResponse post(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
    }

    public abstract String getControllerPath();

    protected String getResourceName(QueryStringDecoder urlDecoder) {
        return urlDecoder.path().substring(PreviewStaticServer.PREFIX.length() + getControllerPath().length());
    }

    public void addRoute(Map<String, BaseController> route) {
        route.put(PreviewStaticServer.PREFIX + getControllerPath(), this);
    }

}
