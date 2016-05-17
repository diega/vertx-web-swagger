# Eclipse Vert.x/Web Swagger Router

This project aims at having a generic Vert.X Router which is configure dynamically with a swagger defitinion (json format).

## Usage:

```java
    //using swagger-parser library to parse Json Swagger Definition to a Swagger Object
    Swagger swagger = new SwaggerParser().parse(readFile.result().toString(Charset.forName("utf-8")));
    
    //using this Swagger object to create a Vert.X Router
    Router swaggerRouter = SwaggerRouter.swaggerRouter(Router.router(vertx), swagger, vertx.eventBus());
```

### Example:
With this Json

    {
      "swagger" : "2.0",
      "info" : {
        "version" : "1.0.0",
        "title" : "Swagger Test"
      },
      "paths" : {
        "/hello" : {
          "get" : {
            "operationId" : "test.dummy"
          }
        }
      }
    }

The SwaggerRouter will be automatically configured as if you've done this
```java
    router.get("/hello").handler(req -> {
        vertx.eventBus().send("GET_hello", null, result -> {
            if (result.succeeded()) {
                req.response().write(result.toString());
            } else {
                req.fail(result.cause());
            }
        });
    });
```
