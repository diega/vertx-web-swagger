package com.chamfroy.vertx.swagger.extractors;

import io.swagger.models.parameters.Parameter;
import io.vertx.ext.web.RoutingContext;

public interface ParameterExtractor {
    Object extract(String name, Parameter parameter, RoutingContext context);
}
