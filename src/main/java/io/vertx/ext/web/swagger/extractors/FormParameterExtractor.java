package io.vertx.ext.web.swagger.extractors;

import io.swagger.models.parameters.FormParameter;
import io.swagger.models.parameters.Parameter;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

public class FormParameterExtractor implements ParameterExtractor {
    @Override
    public Object extract(String name, Parameter parameter, RoutingContext context) {
        FormParameter formParam = (FormParameter) parameter;
        MultiMap params = context.request().formAttributes();
        if (!params.contains(name) && formParam.getRequired()) {
            throw new IllegalArgumentException("Missing required parameter: " + name);
        }
        if (formParam.getType().equals("array"))
            return params.getAll(name);
        if (formParam.getType().equals("file")) {
            String uploadedFileName = null;
            for (FileUpload file : context.fileUploads()) {
                if (file.name().equals(name)) {
                    uploadedFileName = file.uploadedFileName();
                    break;
                }
            }
            return uploadedFileName;
        }
        return params.get(name);
    }
}
