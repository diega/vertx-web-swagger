package com.chamfroy.vertx.swagger;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.Charset;

@RunWith(VertxUnitRunner.class)
public class BuildRouterTest {

    private static final int TEST_PORT = 9292;
    private static final String TEST_HOST = "localhost";
    private Vertx vertx;
    private EventBus eventBus;

    @Before
    public void before(TestContext context) {
        Async before = context.async();
        vertx = Vertx.vertx();
        eventBus = vertx.eventBus();
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
    }

    @Test
    public void testBasicRequest(TestContext context) {
        Async testBasicRequest = context.async();
        eventBus.<JsonObject>consumer("test.dummy").handler(testDummyEvent -> {
            testDummyEvent.reply(new JsonObject().put("hola", "mundo"));
        }).completionHandler(registerHandler -> {
            if (registerHandler.succeeded()) {
                HttpClientOptions options = new HttpClientOptions();
                options.setDefaultPort(TEST_PORT);
                HttpClient httpClient = vertx.createHttpClient();
                httpClient.getNow(TEST_PORT, TEST_HOST, "/hello", response -> {
                    response.bodyHandler(body -> {
                        JsonObject jsonBody = new JsonObject(body.toString(Charset.forName("utf-8")));
                        context.assertTrue(jsonBody.containsKey("hola"));
                        context.assertEquals("mundo", jsonBody.getString("hola"));
                        testBasicRequest.complete();
                    });
                });
            } else {
                context.fail(registerHandler.cause());
            }
        });
    }
}
