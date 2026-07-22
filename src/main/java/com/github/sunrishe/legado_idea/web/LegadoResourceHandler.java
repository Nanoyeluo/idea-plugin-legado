package com.github.sunrishe.legado_idea.web;

import com.intellij.openapi.diagnostic.Logger;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public final class LegadoResourceHandler extends CefResourceHandlerAdapter {
    private static final Logger LOG = Logger.getInstance(LegadoResourceHandler.class);
    private static final String RESOURCE_ROOT = "web";
    private static final String INJECTION = "<script>" +
            "localStorage.setItem('legadoWebServeUrl','%s');" +
            "window.acquireVsCodeApi = function(){" +
            "  return {" +
            "    postMessage: function(msg){ if(window.__legadoIdeaQuery) window.__legadoIdeaQuery(JSON.stringify(msg)); }," +
            "    setState: function(){}," +
            "    getState: function(){ return null; }" +
            "  };" +
            "};" +
            "window.__legadoIdeaQueue = [];" +
            "window.__legadoIdeaQuery = function(msg){ window.__legadoIdeaQueue.push(msg); };" +
            "</script>";

    private final String webServeUrl;
    private ByteArrayInputStream dataStream;
    private String mimeType;
    private int contentLength;

    public LegadoResourceHandler(String webServeUrl) {
        this.webServeUrl = webServeUrl;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        String url = request.getURL();
        LOG.info("LegadoResourceHandler.processRequest: " + url + ", webServeUrl=" + webServeUrl);
        String relativePath = url.replaceFirst("http://legado-idea/", "");
        int queryIndex = relativePath.indexOf('?');
        if (queryIndex >= 0) {
            relativePath = relativePath.substring(0, queryIndex);
        }
        if (relativePath.isEmpty() || relativePath.endsWith("/")) {
            relativePath = "index.html";
        }

        URL resourceUrl = getClass().getClassLoader().getResource(RESOURCE_ROOT + "/" + relativePath);
        if (resourceUrl == null) {
            LOG.warn("Missing web resource: " + relativePath);
            callback.cancel();
            return false;
        }

        try {
            URLConnection connection = resourceUrl.openConnection();
            byte[] data;
            try (InputStream in = connection.getInputStream()) {
                data = in.readAllBytes();
            }
            if ("index.html".equals(relativePath)) {
                String html = new String(data, StandardCharsets.UTF_8);
                String script = String.format(INJECTION, escapeJsString(webServeUrl));
                html = html.replaceFirst("(?i)(<head[^>]*>)", "$1" + script);
                data = html.getBytes(StandardCharsets.UTF_8);
            }
            dataStream = new ByteArrayInputStream(data);
            contentLength = data.length;

            mimeType = connection.getContentType();
            if (mimeType == null || "content/unknown".equals(mimeType)) {
                mimeType = guessMimeType(relativePath);
            }
            callback.Continue();
            return true;
        } catch (IOException e) {
            LOG.error("Failed to serve resource: " + relativePath, e);
            callback.cancel();
            return false;
        }
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        response.setMimeType(mimeType);
        response.setStatus(200);
        responseLength.set(contentLength);
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
        int read = dataStream.read(dataOut, 0, bytesToRead);
        bytesRead.set(Math.max(read, 0));
        return read > 0;
    }

    @Override
    public void cancel() {
        if (dataStream != null) {
            try {
                dataStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String guessMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }

    private static String escapeJsString(String value) {
        return value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
