package com.yahoo.container.core;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.application.UriPattern;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Common conversion from a handled http request to a metric context.
 *
 * @author jonmv
 */
public class HandlerMetricContextUtil {

    public static Metric.Context contextFor(Request request, Metric metric, Class<?> handlerClass) {
        return contextFor(request, Map.of(), metric, handlerClass);
    }

    public static Metric.Context contextFor(Request request, Map<String, String> extraDimensions, Metric metric, Class<?> handlerClass) {
        BindingMatch<?> match = request.getBindingMatch();
        if (match == null) return null;
        UriPattern matched = match.matched();
        if (matched == null) return null;
        String name = matched.toString();
        String endpoint = request.headers().containsKey("Host") ? request.headers().get("Host").get(0) : null;

        Map<String, String> dimensions = new HashMap<>();
        dimensions.put("handler", name);
        if (endpoint != null) {
            dimensions.put("endpoint", endpoint);
        }
        URI uri = request.getUri();
        dimensions.put("scheme", uri.getScheme());
        dimensions.put("port", Integer.toString(uri.getPort()));
        dimensions.put("handler-name", handlerClass.getName());
        dimensions.putAll(extraDimensions);
        return metric.createContext(dimensions);
    }

}
