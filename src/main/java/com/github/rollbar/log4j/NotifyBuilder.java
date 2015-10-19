package com.github.rollbar.log4j;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public class NotifyBuilder {

    private static final String NOTIFIER_VERSION = "0.2";

    private final String accessToken;
    private final String environment;
    private final String rollbarContext;

    private final JSONObject notifierData;
    private final JSONObject serverData;
    protected static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(2,
            new ThreadFactory() {
                public Thread newThread(Runnable runnable) {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    return thread;
                }
            }
    );


    public NotifyBuilder(String accessToken, String environment, String rollbarContext) throws JSONException, UnknownHostException {
        this.accessToken = accessToken;
        this.environment = environment;
        this.rollbarContext = rollbarContext;
        this.notifierData = getNotifierData();
        this.serverData = getServerData();
    }


    private String getValue(String key, Map<String, String> context, String defaultValue) {
        if (context == null) return defaultValue;
        Object value = context.get(key);
        if (value == null) return defaultValue;
        return value.toString();
    }

    public JSONObject build(String level, String message, Throwable throwable, Map<String, String> context) throws JSONException {

        JSONObject payload = new JSONObject();

        // access token
        payload.put("access_token", this.accessToken);

        // data
        JSONObject data = new JSONObject();

        // general values
        data.put("environment", this.environment);
        data.put("level", level);
        data.put("platform", getValue("platform", context, "java"));
        data.put("framework", getValue("framework", context, "java"));
        data.put("language", "java");
        if (rollbarContext != null && !rollbarContext.isEmpty())
            data.put("context", rollbarContext);
        data.put("timestamp", System.currentTimeMillis() / 1000);
        data.put("body", getBody(message, throwable));
        data.put("request", buildRequest(context));

        // Custom data and log message if there's a throwable
        JSONObject customData = buildCustom(context);
        if (throwable != null && message != null) {
            customData.put("log", message);
        }

        data.put("custom", customData);
        data.put("client", buildClient(context));
        data.put("server", serverData);
        data.put("notifier", notifierData);
        payload.put("data", data);

        return payload;
    }

    private JSONObject buildClient(Map<String, String> ctx) {
        JSONObject client = new JSONObject();
        JSONObject javaScript = new JSONObject();
        javaScript.put("browser", ctx.get(RollbarFilter.REQUEST_USER_AGENT));
        client.put("javascript", javaScript);
        return client;
    }

    private JSONObject buildCustom(Map<String, String> ctx) {
        JSONObject custom = new JSONObject();
        for (Entry<String, String> ctxEntry : ctx.entrySet()) {
            String key = ctxEntry.getKey();
            if (key.startsWith(RollbarFilter.REQUEST_PREFIX))
                continue;
            custom.put(key, ctxEntry.getValue());
        }
        return custom;
    }

    private String stripPrefix(String value, String prefix) {
        return value.substring(prefix.length(), value.length());
    }

    private JSONObject buildRequest(Map<String, String> ctx) {
        JSONObject request = new JSONObject();
        request.put("url", ctx.get(RollbarFilter.REQUEST_URL));
        request.put("query_string", ctx.get(RollbarFilter.REQUEST_QS));

        JSONObject headers = new JSONObject();
        JSONObject params = new JSONObject();

        for (Entry<String, String> ctxEntry : ctx.entrySet()) {
            String key = ctxEntry.getKey();
            if (key.startsWith(RollbarFilter.REQUEST_HEADER_PREFIX)) {
                headers.put(stripPrefix(key, RollbarFilter.REQUEST_HEADER_PREFIX), ctxEntry.getValue());
            } else if (key.startsWith(RollbarFilter.REQUEST_PARAM_PREFIX)) {
                params.put(stripPrefix(key, RollbarFilter.REQUEST_PARAM_PREFIX), ctxEntry.getValue());
            }
        }

        request.put("headers", headers);

        String method = ctx.get(RollbarFilter.REQUEST_METHOD);
        if (method != null) {
            request.put("method", method);
            if(method.equals("GET")){
                request.put("GET", params);
            }else if(method.equals("POST")){
                request.put("POST", params);
            }
        }


        request.put("user_ip", ctx.get(RollbarFilter.REQUEST_REMOTE_ADDR));
        return request;
    }

    private JSONObject getBody(String message, Throwable original) throws JSONException {
        JSONObject body = new JSONObject();

        Throwable throwable = original;

        if (throwable != null) {
            List<JSONObject> traces = new ArrayList<JSONObject>();
            do {
                traces.add(0, createTrace(throwable));
                throwable = throwable.getCause();
            } while (throwable != null);

            body.put("trace_chain", new JSONArray(traces));
        }

        if (original == null && message != null) {
            JSONObject messageBody = new JSONObject();
            messageBody.put("body", message);
            body.put("message", messageBody);
        }

        return body;
    }

    private JSONObject getNotifierData() throws JSONException {
        JSONObject notifier = new JSONObject();
        notifier.put("name", "rollbar-java");
        notifier.put("version", NOTIFIER_VERSION);
        return notifier;
    }

    private JSONObject getServerData() throws JSONException, UnknownHostException {

        InetAddress localhost = InetAddress.getLocalHost();

        String host = localhost.getHostName();
        String ip = localhost.getHostAddress();

        JSONObject notifier = new JSONObject();
        notifier.put("host", host);
        notifier.put("ip", ip);
        return notifier;
    }

    private JSONObject createTrace(Throwable throwable) throws JSONException {
        JSONObject trace = new JSONObject();

        JSONArray frames = new JSONArray();

        StackTraceElement[] elements = throwable.getStackTrace();
        for (int i = elements.length - 1; i >= 0; --i) {
            StackTraceElement element = elements[i];

            JSONObject frame = new JSONObject();

            frame.put("class_name", element.getClassName());
            frame.put("filename", element.getFileName());
            frame.put("method", element.getMethodName());

            if (element.getLineNumber() > 0) {
                frame.put("lineno", element.getLineNumber());
            }

            frames.put(frame);
        }

        JSONObject exceptionData = new JSONObject();
        exceptionData.put("class", throwable.getClass().getName());
        exceptionData.put("message", throwable.getMessage());

        trace.put("frames", frames);
        trace.put("exception", exceptionData);

        return trace;
    }



}
