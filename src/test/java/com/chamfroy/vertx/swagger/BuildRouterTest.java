package com.chamfroy.vertx.swagger;

import java.nio.charset.Charset;
import java.util.concurrent.TimeoutException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;

@RunWith(VertxUnitRunner.class)
public class BuildRouterTest {

    private static final int TEST_PORT = 9292;
    private static final String TEST_HOST = "localhost";
    private static Vertx vertx;
    private static EventBus eventBus;
    private static HttpClient httpClient;

    @BeforeClass
    public static void beforClass(TestContext context) {
        Async before = context.async();
        vertx = Vertx.vertx();
        eventBus = vertx.eventBus();

        // init Router
        FileSystem vertxFileSystem = vertx.fileSystem();
        vertxFileSystem.readFile("swagger.json", readFile -> {
            if (readFile.succeeded()) {
                Swagger swagger = new SwaggerParser().parse(readFile.result().toString(Charset.forName("utf-8")));
                Router swaggerRouter = SwaggerRouter.swaggerRouter(Router.router(vertx), swagger, eventBus);
                vertx.createHttpServer().requestHandler(swaggerRouter::accept).listen(TEST_PORT, TEST_HOST, listen -> {
                    if (listen.succeeded()) {
                        before.complete();
                    } else {
                        context.fail(listen.cause());
                    }
                });
            } else {
                context.fail(readFile.cause());
            }
        });

        // init consumers
        eventBus.<JsonObject> consumer("GET_store_inventory").handler(message -> {
            message.reply(new JsonObject().put("sold", 2L));
        });
        eventBus.<JsonObject> consumer("test.dummy").handler(message -> {
            context.fail("should not be called");
        });
        eventBus.<JsonObject> consumer("GET_pet_petId").handler(message -> {
            String petId = message.body().getString("petId");
            message.reply(new JsonObject().put("petId_received", petId));
        });
        eventBus.<JsonObject> consumer("GET_user_login").handler(message -> {
            String username = message.body().getString("username");
            message.reply(new JsonObject().put("username_received", username));
        });
        eventBus.<JsonObject> consumer("GET_pet_findByStatus").handler(message -> {
            JsonArray status = message.body().getJsonArray("status");
            JsonObject result = new JsonObject();
            for (int i = 0; i < status.size(); i++) {
                result.put("element " + i, status.getString(i));
            }
            message.reply(result);
        });

        // init http Server
        HttpClientOptions options = new HttpClientOptions();
        options.setDefaultPort(TEST_PORT);
        httpClient = Vertx.vertx().createHttpClient();

    }

    @Test(timeout = 2000)
    public void testResourceNotfound(TestContext context) {
        Async async = context.async();
        httpClient.getNow(TEST_PORT, TEST_HOST, "/dummy", response -> {
            context.assertEquals(response.statusCode(), 404);
            async.complete();
        });

    }

    @Test(timeout = 2000)
    public void testMessageIsConsume(TestContext context) {
        Async async = context.async();
        httpClient.getNow(TEST_PORT, TEST_HOST, "/store/inventory", response -> {
            response.bodyHandler(body -> {
                JsonObject jsonBody = new JsonObject(body.toString(Charset.forName("utf-8")));
                context.assertTrue(jsonBody.containsKey("sold"));
                context.assertEquals(2L, jsonBody.getLong("sold"));
                async.complete();
            });
        });
    }

    @Test(timeout = 2000)
    public void testMessageIsNotConsume(TestContext context) {
        Async async = context.async();
        HttpClientRequest req = httpClient.get(TEST_PORT, TEST_HOST, "/store/inventory");
        req.setTimeout(1000);
        req.exceptionHandler(err -> {
            context.assertEquals(err.getClass(), TimeoutException.class);
            async.complete();
        });
    }

    @Test(timeout = 2000)
    public void testWithPathParameter(TestContext context) {
        Async async = context.async();
        httpClient.getNow(TEST_PORT, TEST_HOST, "/pet/5", response -> {
            response.bodyHandler(body -> {
                JsonObject jsonBody = new JsonObject(body.toString(Charset.forName("utf-8")));
                context.assertTrue(jsonBody.containsKey("petId_received"));
                context.assertEquals("5", jsonBody.getString("petId_received"));
                async.complete();
            });
        });
    }

    @Test(timeout = 2000)
    public void testWithQuerySimpleParameter(TestContext context) {
        Async async = context.async();
        httpClient.getNow(TEST_PORT, TEST_HOST, "/user/login?username=myUser&password=mySecret", response -> {
            response.bodyHandler(body -> {
                JsonObject jsonBody = new JsonObject(body.toString(Charset.forName("utf-8")));
                context.assertTrue(jsonBody.containsKey("username_received"));
                context.assertEquals("myUser", jsonBody.getString("username_received"));
                async.complete();
            });
        });
    }

    @Test(timeout = 2000)
    public void testWithQueryArrayParameter(TestContext context) {
        Async async = context.async();
        httpClient.getNow(TEST_PORT, TEST_HOST, "/pet/findByStatus?status=available", response -> {
            response.bodyHandler(body -> {
                JsonObject jsonBody = new JsonObject(body.toString(Charset.forName("utf-8")));
                context.assertTrue(jsonBody.containsKey("element 0"));
                context.assertEquals("available", jsonBody.getString("element 0"));
                async.complete();
            });
        });
    }

    @Test(timeout = 2000)
    public void testWithQueryManyArrayParameter(TestContext context) {
        Async async = context.async();
        httpClient.getNow(TEST_PORT, TEST_HOST, "/pet/findByStatus?status=available&status=pending", response -> {
            response.bodyHandler(body -> {
                JsonObject jsonBody = new JsonObject(body.toString(Charset.forName("utf-8")));
                context.assertTrue(jsonBody.containsKey("element 0"));
                context.assertEquals("available", jsonBody.getString("element 0"));
                context.assertTrue(jsonBody.containsKey("element 1"));
                context.assertEquals("pending", jsonBody.getString("element 1"));
                async.complete();
            });
        });
    }
}
