package com.pulseload.engine.executor;

import com.pulseload.model.HttpMethod;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExecutorTest {

    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/ok", exchange -> respond(exchange, 200));
        server.createContext("/not-found", exchange -> respond(exchange, 404));
        server.createContext("/boom", exchange -> respond(exchange, 500));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200);
        });

        server.start();
        baseUri = URI.create("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void reportsSuccessOutcomeForA2xxResponse() {
        try (ProtocolExecutor executor = new HttpExecutor(baseUri.resolve("/ok"), HttpMethod.GET, Duration.ofSeconds(5))) {
            ExecutionResult result = executor.execute();

            assertThat(result.outcome()).isEqualTo(ExecutionOutcome.SUCCESS_2XX);
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.serviceTimeNanos()).isPositive();
        }
    }

    @Test
    void reportsClientErrorOutcomeForA4xxResponse() {
        try (ProtocolExecutor executor = new HttpExecutor(baseUri.resolve("/not-found"), HttpMethod.GET, Duration.ofSeconds(5))) {
            ExecutionResult result = executor.execute();

            assertThat(result.outcome()).isEqualTo(ExecutionOutcome.CLIENT_ERROR_4XX);
            assertThat(result.statusCode()).isEqualTo(404);
        }
    }

    @Test
    void reportsServerErrorOutcomeForA5xxResponse() {
        try (ProtocolExecutor executor = new HttpExecutor(baseUri.resolve("/boom"), HttpMethod.GET, Duration.ofSeconds(5))) {
            ExecutionResult result = executor.execute();

            assertThat(result.outcome()).isEqualTo(ExecutionOutcome.SERVER_ERROR_5XX);
            assertThat(result.statusCode()).isEqualTo(500);
        }
    }

    @Test
    void reportsTimeoutOutcomeWhenTheTargetIsSlowerThanTheRequestTimeout() {
        try (ProtocolExecutor executor = new HttpExecutor(baseUri.resolve("/slow"), HttpMethod.GET, Duration.ofMillis(200))) {
            ExecutionResult result = executor.execute();

            assertThat(result.outcome()).isEqualTo(ExecutionOutcome.TIMEOUT);
        }
    }

    @Test
    void reportsConnectErrorWhenNoServerIsListening() {
        try (ProtocolExecutor executor = new HttpExecutor(URI.create("http://localhost:1"), HttpMethod.GET, Duration.ofSeconds(2))) {
            ExecutionResult result = executor.execute();

            assertThat(result.outcome()).isEqualTo(ExecutionOutcome.CONNECT_ERROR);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
}
