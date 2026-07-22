package com.github.sunrishe.legado_idea.web;

import com.intellij.openapi.diagnostic.Logger;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public final class LegadoResourceHandler extends CefResourceHandlerAdapter {
    private static final Logger LOG = Logger.getInstance(LegadoResourceHandler.class);
    private static final String RESOURCE_ROOT = "web";
    private static final String API_PREFIX = "api/";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
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
    private int statusCode;
    private Map<String, String> responseHeaders;

    public LegadoResourceHandler(String webServeUrl) {
        this.webServeUrl = webServeUrl;
        this.statusCode = 200;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback) {
        String url = request.getURL();
        LOG.info("LegadoResourceHandler.processRequest: " + url + ", webServeUrl=" + webServeUrl);
        String relativePath = url.replaceFirst("http://legado-idea/", "");
        int queryIndex = relativePath.indexOf('?');
        String pathOnly = queryIndex >= 0 ? relativePath.substring(0, queryIndex) : relativePath;

        if (pathOnly.startsWith(API_PREFIX)) {
            return proxyApiRequest(request, callback, relativePath);
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
            statusCode = 200;
            responseHeaders = new HashMap<>();
            callback.Continue();
            return true;
        } catch (IOException e) {
            LOG.error("Failed to serve resource: " + relativePath, e);
            callback.cancel();
            return false;
        }
    }

    private boolean proxyApiRequest(CefRequest request, CefCallback callback, String relativePath) {
        if (webServeUrl == null || webServeUrl.isBlank()) {
            LOG.warn("Cannot proxy API request, webServeUrl is empty");
            callback.cancel();
            return false;
        }

        String apiPath = relativePath.substring(API_PREFIX.length());
        String targetUrl = webServeUrl + "/" + apiPath;
        LOG.info("Proxying API request to: " + targetUrl);

        try {
            String method = request.getMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                setupPreflightResponse(callback);
                return true;
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(targetUrl));

            Map<String, String> headerMap = new HashMap<>();
            request.getHeaderMap(headerMap);
            headerMap.forEach((name, value) -> {
                String lower = name.toLowerCase();
                if (!lower.equals("host") && !lower.equals("origin") &&
                        !lower.equals("referer") && !lower.equals("connection") &&
                        !lower.equals("content-length")) {
                    builder.header(name, value);
                }
            });

            byte[] body = readPostBody(request);
            switch (method.toUpperCase()) {
                case "GET":
                    builder.GET();
                    break;
                case "POST":
                    builder.POST(HttpRequest.BodyPublishers.ofByteArray(body != null ? body : new byte[0]));
                    break;
                case "PUT":
                    builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body != null ? body : new byte[0]));
                    break;
                case "DELETE":
                    builder.DELETE();
                    break;
                default:
                    builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body != null ? body : new byte[0]));
                    break;
            }

            HttpResponse<InputStream> httpResponse = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] responseBody = httpResponse.body().readAllBytes();

            dataStream = new ByteArrayInputStream(responseBody);
            contentLength = responseBody.length;
            statusCode = httpResponse.statusCode();
            mimeType = httpResponse.headers().firstValue("Content-Type").orElse("application/json");

            responseHeaders = new HashMap<>();
            httpResponse.headers().map().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    responseHeaders.put(name, values.get(0));
                }
            });

            callback.Continue();
            return true;
        } catch (Exception e) {
            LOG.error("Failed to proxy API request: " + targetUrl, e);
            callback.cancel();
            return false;
        }
    }

    private void setupPreflightResponse(CefCallback callback) {
        dataStream = new ByteArrayInputStream(new byte[0]);
        contentLength = 0;
        statusCode = 204;
        mimeType = "text/plain";
        responseHeaders = new HashMap<>();
        callback.Continue();
    }

    private byte[] readPostBody(CefRequest request) {
        CefPostData postData = request.getPostData();
        if (postData == null) {
            return null;
        }
        Vector<CefPostDataElement> elements = new Vector<>();
        postData.getElements(elements);
        for (CefPostDataElement element : elements) {
            if (element.getType() == CefPostDataElement.Type.PDE_TYPE_BYTES) {
                int size = element.getBytesCount();
                byte[] buffer = new byte[size];
                int read = element.getBytes(size, buffer);
                return Arrays.copyOf(buffer, read);
            }
        }
        return null;
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        response.setMimeType(mimeType);
        response.setStatus(statusCode);
        responseLength.set(contentLength);
        if (responseHeaders != null) {
            response.setHeaderMap(responseHeaders);
        }
        response.setHeaderByName("Access-Control-Allow-Origin", "*", true);
        response.setHeaderByName("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS", true);
        response.setHeaderByName("Access-Control-Allow-Headers", "*", true);
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
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
