package com.shuzijun.leetcode.plugin.model;

import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.util.io.FileUtilRt;
import com.shuzijun.leetcode.plugin.utils.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author shuzijun
 */
public class Graphql {

    private String operationName;

    private Map variables;

    private String query;

    private Graphql(String operationName, Map variables, String query) {
        this.operationName = operationName;
        this.variables = variables;
        this.query = query;
    }

    public String getOperationName() {
        return operationName;
    }

    public Map getVariables() {
        return variables;
    }

    public String getQuery() {
        return query;
    }

    public String generate() {
        return JSONObject.toJSONString(this);
    }

    public static GraphqlBuilder builder() {
        return new GraphqlBuilder();
    }

    public static class GraphqlBuilder {

        private static final String PATH = "/graphql/";

        private static final String CNSUFFIX = "_cn.graphql";

        private static final Map<String, String> QUERY_CACHE = new ConcurrentHashMap<>();

        private String url = URLUtils.getLeetcodeGraphql();

        private String operationName;

        private Map variables = new HashMap();

        private String query;

        private String suffix = ".graphql";

        private boolean cache = false;

        private String cacheParam;

        private GraphqlBuilder() {

        }

        public GraphqlBuilder url(String url) {
            this.url = url;
            return this;
        }

        public GraphqlBuilder cn(boolean isCn) {
            if (isCn) {
                this.suffix = CNSUFFIX;
            }
            return this;
        }

        public GraphqlBuilder operationName(String operationName) {
            this.operationName = operationName;
            this.query(operationName);
            return this;
        }

        public GraphqlBuilder operationName(String operationName, String operationNameAlias) {
            this.operationName = operationNameAlias;
            this.query(operationName);
            return this;
        }

        private GraphqlBuilder query(String operationName) {
            String key = PATH + operationName + suffix;
            String cached = QUERY_CACHE.get(key);
            if (cached != null) {
                this.query = cached;
                return this;
            }
            try (InputStream inputStream = GraphqlBuilder.class.getResourceAsStream(key)) {
                if (inputStream == null) {
                    LogUtils.LOG.error(key + " Path is empty");
                } else {
                    String loaded = new String(FileUtilRt.loadBytes(inputStream));
                    QUERY_CACHE.put(key, loaded);
                    this.query = loaded;
                }
            } catch (IOException e) {
                LogUtils.LOG.error(key + " Loading exception", e);
            }
            return this;
        }

        public GraphqlBuilder variables(String key, Object value) {
            variables.put(key, value);
            return this;
        }

        public GraphqlBuilder cache(boolean cache) {
            this.cache = cache;
            return this;
        }

        public GraphqlBuilder cacheParam(String cacheParam) {
            // null 表示未選擇快取：request() 對所有請求都會呼叫本方法，
            // 無條件開快取會讓 userStatus 等即時性請求吃到過期回應
            if (cacheParam != null) {
                this.cacheParam = cacheParam;
                this.cache = true;
            }
            return this;
        }

        public Graphql build() {
            return new Graphql(operationName, variables, query);
        }

        @NotNull
        public HttpResponse request() {
            return HttpRequest.builderPost(url, "application/json")
                    .body(build().generate())
                    .addHeader("Accept", "application/json")
                    .cache(cache).cacheParam(cacheParam).request();
        }
    }

}
