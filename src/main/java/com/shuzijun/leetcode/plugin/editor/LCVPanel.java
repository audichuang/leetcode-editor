package com.shuzijun.leetcode.plugin.editor;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.jcef.JCEFHtmlPanel;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import com.intellij.util.net.ProxyConfiguration;
import com.intellij.util.net.ProxySettings;
import com.intellij.util.ui.StartupUiUtil;
import com.shuzijun.leetcode.plugin.model.PluginConstant;
import com.shuzijun.leetcode.plugin.utils.FileUtils;
import com.shuzijun.leetcode.plugin.utils.PropertiesUtils;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.apache.commons.lang3.StringUtils;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.*;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.List;

/**
 * @author shuzijun
 */
public class LCVPanel extends JCEFHtmlPanel {

    private static final Logger LOG = Logger.getInstance(LCVPanel.class);

    private final Url servicePath = BuiltInServerManager.getInstance().addAuthToken(Urls.parseEncoded("http://localhost:" + BuiltInServerManager.getInstance().getPort() + PreviewStaticServer.PREFIX));
    // 本地内建server的scheme+host+port，用于判断资源请求是不是打到本地服务（本地的不需要代理）
    private final String localServicePrefix = servicePath.getScheme() + URLUtil.SCHEME_SEPARATOR + servicePath.getAuthority();
    private String templateHtmlFile = "template/default.html";

    private CefRequestHandler requestHandler;
    private CefLifeSpanHandler lifeSpanHandler;

    private final String url;
    private final String text;
    private final Project project;
    private final Set<String> iframe = Collections.synchronizedSet(new HashSet<>());
    private static volatile String templateCache;
    private static final List<String> headers = Arrays.asList(HttpHeaderNames.CONTENT_SECURITY_POLICY.toString(), HttpHeaderNames.CONTENT_ENCODING.toString()
            , HttpHeaderNames.CONTENT_LENGTH.toString());

    public LCVPanel(@Nullable String url, Project project, String text, boolean old) {
        super(null);
        this.url = url;
        this.project = project;
        this.text = text;
        init();
    }

    public LCVPanel(@Nullable String url, Project project, String text) {
        super(null, null);
        this.url = url;
        this.project = project;
        this.text = text;
        init();
    }

    private void init() {
        getJBCefClient().addRequestHandler(requestHandler = new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean user_gesture, boolean is_redirect) {
                String requestUrl = request.getURL();
                if (requestUrl.startsWith(url)) {
                    return false;
                } else if (!user_gesture) {
                    iframe.add(requestUrl);
                    return false;
                } else {
                    openUrl(URLDecoder.decode(requestUrl, StandardCharsets.UTF_8));
                    return true;
                }
            }

            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator, BoolRef disableDefaultHandling) {
                String requestUrl = request.getURL();
                boolean isIframeRequest = iframe.contains(requestUrl);
                // 题目描述里的<img>直连LeetCode CDN时JCEF会忽略IDE的proxy设置导致图片加载失败，
                // 这里对非本地服务的图片请求也走和iframe一样的代理通道；
                // 仅在用户配置了IDE proxy时才需要这个中转，未配置proxy时JCEF可直连，维持原行为（避免#553引入的回归）
                boolean useProxy = !(ProxySettings.getInstance().getProxyConfiguration() instanceof ProxyConfiguration.DirectProxy);
                boolean isRemoteImage = useProxy && !isIframeRequest && request.getResourceType() == CefRequest.ResourceType.RT_IMAGE
                        && requestUrl.startsWith(URLUtil.HTTP_PROTOCOL) && !requestUrl.startsWith(localServicePrefix);
                if (!isIframeRequest && !isRemoteImage) {
                    return null;
                }

                return new CefResourceRequestHandlerAdapter() {

                    @Override
                    public CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
                        try {
                            return HttpRequests.request(request.getURL())
                                    .throwStatusCodeException(false)
                                    .connect(new HttpRequests.RequestProcessor<CefResourceHandler>() {
                                        @Override
                                        public CefResourceHandler process(HttpRequests.Request request) throws IOException {
                                            HttpURLConnection urlConnection = (HttpURLConnection) request.getConnection();
                                            Map<String, String> header = new HashMap<>();
                                            urlConnection.getHeaderFields().forEach((key, values) -> {
                                                if (key != null && values != null && !headers.contains(key.toLowerCase())) {
                                                    header.put(key, StringUtils.join(values.toArray(), ";"));
                                                }
                                            });
                                            if (isRemoteImage) {
                                                return new ProxyLoadHtmlResourceHandler(request.readBytes(null), header, urlConnection.getResponseCode());
                                            }
                                            return new ProxyLoadHtmlResourceHandler(request.readString(), header, urlConnection.getResponseCode());
                                        }
                                    });
                        } catch (IOException io) {

                            return null;
                        }
                    }
                };
            }
        }, getCefBrowser());
        getJBCefClient().addLifeSpanHandler(lifeSpanHandler = new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String target_url, String target_frame_name) {
                if (!target_url.startsWith(url)) {
                    openUrl(URLDecoder.decode(target_url, StandardCharsets.UTF_8));
                }
                return true;
            }
        }, getCefBrowser());
        loadHTML(createHtml(text), url);
    }

    public void reloadText() {
        iframe.clear();
        loadHTML(createHtml(text), url);
    }

    @Override
    public void dispose() {
        getJBCefClient().removeRequestHandler(requestHandler, getCefBrowser());
        getJBCefClient().removeLifeSpanHandler(lifeSpanHandler, getCefBrowser());
        super.dispose();
    }

    private void openUrl(String url) {
        if (url.startsWith(URLUtil.FILE_PROTOCOL)) {
            File file = new File(url.substring((URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + FileUtils.separator()).length()));
            if (!file.exists()) {
                Notifications.Bus.notify(new Notification(PluginConstant.NOTIFICATION_GROUP, "Cannot Open File", file.getPath() + " not exist", NotificationType.INFORMATION), project);
            } else if (file.isDirectory()) {
                Notifications.Bus.notify(new Notification(PluginConstant.NOTIFICATION_GROUP, "Cannot Open Directory", file.getPath() + " is a directory", NotificationType.INFORMATION), project);
            } else {
                // VFS refresh 放背景緒，避免預覽點本地檔案連結時同步 refresh 凍住 EDT
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        FileEditor[] editors = FileEditorManager.getInstance(project).openFile(vf, false);
                        if (editors == null || editors.length == 0) {
                            FileType fileType = FileTypeChooser.getKnownFileTypeOrAssociate(vf, project);
                            if (fileType == null || fileType == FileTypes.UNKNOWN) {
                                return;
                            } else {
                                FileEditorManager.getInstance(project).openFile(vf, false);
                            }
                        }
                    });
                });
            }
        } else {
            BrowserUtil.browse(url);
        }
    }

    private String createHtml(String text) {
        try {
            String template = templateCache;
            if (template == null) {
                try (InputStream inputStream = PreviewStaticServer.class.getResourceAsStream("/" + templateHtmlFile)) {
                    template = new String(FileUtilRt.loadBytes(inputStream));
                }
                templateCache = template;
            }
            return template.replace("{{service}}", servicePath.getScheme() + URLUtil.SCHEME_SEPARATOR + servicePath.getAuthority() + servicePath.getPath())
                    .replace("{{serverToken}}", org.apache.commons.lang3.StringUtils.isNotBlank(servicePath.getParameters()) ? servicePath.getParameters().substring(1) : "")
                    .replace("{{fileValue}}", text)
                    .replace("{{Lang}}", PropertiesUtils.getInfo("Lang"))
                    .replace("{{darcula}}", StartupUiUtil.INSTANCE.isDarkTheme() + "")
                    .replace("{{ideStyle}}", getStyle(true))
                    ;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getStyle(boolean isTag) {
        try {
            EditorColorsSchemeImpl editorColorsScheme = (EditorColorsSchemeImpl) EditorColorsManager.getInstance().getGlobalScheme();
            Color defaultBackground = editorColorsScheme.getDefaultBackground();

            Color scrollbarThumbColor = EditorColors.SCROLLBAR_THUMB_COLOR.getDefaultColor();
            if (editorColorsScheme.getColor(EditorColors.SCROLLBAR_THUMB_COLOR) != null) {
                scrollbarThumbColor = editorColorsScheme.getColor(EditorColors.SCROLLBAR_THUMB_COLOR);
            }
            TextAttributes textAttributes = editorColorsScheme.getDirectlyDefinedAttributes().get("TEXT");
            Color text = null;
            if (textAttributes != null) {
                text = textAttributes.getForegroundColor();
            }
            String fontFamily = "font-family:\"" + editorColorsScheme.getEditorFontName() + "\",\"Helvetica Neue\",\"Luxi Sans\",\"DejaVu Sans\"," +
                    "\"Hiragino Sans GB\",\"Microsoft Yahei\",sans-serif,\"Apple Color Emoji\",\"Segoe UI Emoji\",\"Noto Color Emoji\",\"Segoe UI Symbol\"," +
                    "\"Android Emoji\",\"EmojiSymbols\";";
            StringBuilder sb = new StringBuilder(isTag ? "<style id=\"ideaStyle\">" : "");
            sb.append(StartupUiUtil.INSTANCE.isDarkTheme() ? ".vditor--dark" : ".vditor").append("{--panel-background-color:").append(toHexColor(defaultBackground))
                    .append(";--textarea-background-color:").append(toHexColor(defaultBackground)).append(";");
            sb.append("--toolbar-background-color:").append(toHexColor(JBColor.background())).append(";");
            sb.append("}");
            sb.append("::-webkit-scrollbar-track {background-color:").append(toHexColor(defaultBackground)).append(";}");
            sb.append("::-webkit-scrollbar-thumb {background-color:").append(toHexColor(scrollbarThumbColor)).append(";}");
            sb.append(".vditor-reset {font-size:").append(editorColorsScheme.getEditorFontSize()).append("px;");
            sb.append(fontFamily);
            if (text != null) {
                sb.append("color:").append(toHexColor(text)).append(";");
            }
            sb.append("}");
            sb.append(" body{background-color: ").append(toHexColor(defaultBackground)).append(";}");
            sb.append(isTag ? "</style>" : "");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }

    }

    private String toHexColor(Color color) {
        DecimalFormat df = new DecimalFormat("0.00");
        DecimalFormatSymbols dfs = new DecimalFormatSymbols();
        dfs.setDecimalSeparator('.');
        df.setDecimalFormatSymbols(dfs);
        return String.format("rgba(%s,%s,%s,%s)", color.getRed(), color.getGreen(), color.getBlue(), df.format(color.getAlpha() / (float) 255));
    }

    public void updateStyle() {
        String style = getStyle(false);
        getCefBrowser().executeJavaScript(
                "updateStyle('" + style + "'," + StartupUiUtil.INSTANCE.isDarkTheme() + ");", getCefBrowser().getURL(), 0);
    }
}