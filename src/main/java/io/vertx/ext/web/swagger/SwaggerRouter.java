package io.vertx.ext.web.swagger;

import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.Swagger;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwaggerRouter {

    private static Logger VERTX_LOGGER = LoggerFactory.getLogger(SwaggerRouter.class);

    private static Pattern PATH_PARAMETERS = Pattern.compile("\\{(.*)\\}");

    private static Map<HttpMethod, RouteBuilder> ROUTE_BUILDERS = new EnumMap<HttpMethod, RouteBuilder>(HttpMethod.class) {{
        put(HttpMethod.POST, Router::post);
        put(HttpMethod.GET, Router::get);
        put(HttpMethod.PUT, Router::put);
        put(HttpMethod.PATCH, Router::patch);
        put(HttpMethod.DELETE, Router::delete);
        put(HttpMethod.HEAD, Router::head);
        put(HttpMethod.OPTIONS, Router::options);
    }};

    private static Map<String, ParameterExtractor> PARAMETER_EXTRACTORS = new HashMap<String, ParameterExtractor>() {{
        put("path", new PathParameterExtractor());
    }};

    public static Router swaggerRouter(Router baseRouter, Swagger swagger, EventBus eventBus) {
        swagger.getPaths().forEach((path, pathDescription) ->
            pathDescription.getOperationMap().forEach((method, operation) -> {
                Route route = ROUTE_BUILDERS.get(method).buildRoute(baseRouter, convertParametersToVertx(path));
                configureRoute(route, operation, eventBus);
            })
        );
        return baseRouter;
    }

    private static void configureRoute(Route route, Operation operation, EventBus eventBus) {
        Optional.ofNullable(operation.getConsumes()).ifPresent(consumes -> consumes.forEach(route::consumes));
        Optional.ofNullable(operation.getProduces()).ifPresent(produces -> produces.forEach(route::produces));

        route.handler(context -> {
            try {
                JsonObject message = new JsonObject();
                operation.getParameters().forEach( parameter -> {
                    String name = parameter.getName();
                    String value = PARAMETER_EXTRACTORS.get(parameter.getIn()).extract(name, parameter.getRequired(), context.request());
                    message.put(name, value);
                });
                eventBus.<JsonObject>send(operation.getOperationId(), message, operationResponse -> {
                    if (operationResponse.succeeded()) {
                        context.response().end(operationResponse.result().body().encode());
                    } else {
                        internalServerErrorEnd(context.response());
                    }
                });
            } catch (RuntimeException e) {
                badRequestEnd(context.response());
            }
        });
    }

    private static String convertParametersToVertx(String path) {
        Matcher pathMatcher = PATH_PARAMETERS.matcher(path);
        return pathMatcher.replaceAll(":$1");
    }

    private static void internalServerErrorEnd(HttpServerResponse response) {
        response.setStatusCode(500).setStatusMessage("Internal Server Error").end();
    }

    private static void badRequestEnd(HttpServerResponse response) {
        response.setStatusCode(400).setStatusMessage("Bad Request").end();
    }

    private interface RouteBuilder {
        Route buildRoute(Router router, String path);
    }

    private interface ParameterExtractor {
        String extract(String name, boolean required, HttpServerRequest request);
    }

    public static class PathParameterExtractor implements ParameterExtractor {
        @Override
        public String extract(String name, boolean required, HttpServerRequest request) {
            MultiMap params = request.params();
            if (!params.contains(name) && required) {
                throw new IllegalArgumentException("Missing required parameter: " + name);
            }
            return params.get(name);
        }
    }
}
