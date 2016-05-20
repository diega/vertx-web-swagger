package io.vertx.ext.web.swagger;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.databind.type.TypeFactory;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.swagger.SwaggerRouter;
import io.vertx.ext.web.swagger.model.User;

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
        eventBus.<JsonObject> consumer("POST_user_createWithArray").handler(message -> {
            try {
                List<User> users = Json.mapper.readValue(message.body().getString("body"), TypeFactory.defaultInstance().constructCollectionType(List.class,  
                        User.class));
                JsonObject result = new JsonObject();
                for (int i = 0; i < users.size(); i++) {
                    result.put("user " + (i+1), users.get(i).toString());
                }
                message.reply(result);
            } catch (Exception e) {
                message.fail(500, e.getLocalizedMessage());
            } 
        });
        eventBus.<JsonObject> consumer("GET_user_logout").handler(message -> {
            message.reply(null);
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

    @Test(timeout = 2000, expected=TimeoutException.class)
    public void testMessageIsNotConsume(TestContext context) {
        Async async = context.async();
        httpClient.getNow(TEST_PORT, TEST_HOST, "/user/logout", response -> 
            response.exceptionHandler(err -> {
                async.complete();
            })
        );
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
    
    @Test(timeout = 2000)
    public void testWithBodyParameterNoBody(TestContext context) {
        Async async = context.async();
        HttpClientRequest req = httpClient.post(TEST_PORT, TEST_HOST, "/user/createWithArray");
        req.handler(response -> {
            context.assertEquals(response.statusCode(), 400);
            async.complete();
        })
        .end();
    }
    
    @Test(timeout = 2000)
    public void testWithBodyParameter(TestContext context) {
        Async async = context.async();
        User user1 = new User(1L, "user 1", "first 1", "last 1", "email 1", "secret 1", "phone 1", 1);
        User user2 = new User(2L, "user 2", "first 2", "last 2", "email 2", "secret 2", "phone 2", 2);
        JsonArray users = new JsonArray(Arrays.asList(user1, user2));
        HttpClientRequest req = httpClient.post(TEST_PORT, TEST_HOST, "/user/createWithArray");
        req.setChunked(true);
        req.handler(response -> 
            response.bodyHandler(result -> {
                JsonObject jsonBody = new JsonObject(result.toString(Charset.forName("utf-8")));
                context.assertTrue(jsonBody.containsKey("user 1"));
                context.assertEquals(user1.toString(), jsonBody.getString("user 1"));
                context.assertTrue(jsonBody.containsKey("user 2"));
                context.assertEquals(user2.toString(), jsonBody.getString("user 2"));
                async.complete();
            })
        ).end(users.encode());
    }
    
    @Test(timeout = 2000)
    public void testNullBodyResponse(TestContext context) {
        Async async = context.async();
        httpClient.getNow(TEST_PORT, TEST_HOST, "/user/logout", response -> {
            context.assertEquals(response.statusCode(), 200);
            async.complete();
        });
    }
}
