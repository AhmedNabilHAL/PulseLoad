package com.pulseload.engine.executor;

import com.pulseload.model.HttpMethod;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Fires blocking HTTP requests on whatever thread calls {@code execute()} — intended to be
 * called from a virtual thread so blocking is cheap. Pinned to HTTP/1.1 so connection reuse
 * and concurrency behave the way a user watching the run would expect; HTTP/2 multiplexing
 * would hide that behind the scenes.
 */
public final class HttpExecutor implements ProtocolExecutor {

    private final HttpClient client;
    private final URI targetUri;
    private final String method;
    private final Duration requestTimeout;

    public HttpExecutor(URI targetUri, HttpMethod method, Duration requestTimeout) {
        this.targetUri = targetUri;
        this.method = method.name();
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(requestTimeout)
                .build();
    }

    @Override
    public ExecutionResult execute() {
        HttpRequest request = HttpRequest.newBuilder(targetUri)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(requestTimeout)
                .build();

        long start = System.nanoTime();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long serviceTimeNanos = System.nanoTime() - start;
            return new ExecutionResult(outcomeFor(response.statusCode()), response.statusCode(), serviceTimeNanos);
        } catch (HttpTimeoutException e) {
            return new ExecutionResult(ExecutionOutcome.TIMEOUT, 0, System.nanoTime() - start);
        } catch (ConnectException e) {
            return new ExecutionResult(ExecutionOutcome.CONNECT_ERROR, 0, System.nanoTime() - start);
        } catch (IOException e) {
            return new ExecutionResult(ExecutionOutcome.CONNECT_ERROR, 0, System.nanoTime() - start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecutionResult(ExecutionOutcome.CONNECT_ERROR, 0, System.nanoTime() - start);
        }
    }

    private static ExecutionOutcome outcomeFor(int statusCode) {
        return switch (statusCode / 100) {
            case 2 -> ExecutionOutcome.SUCCESS_2XX;
            case 3 -> ExecutionOutcome.REDIRECT_3XX;
            case 4 -> ExecutionOutcome.CLIENT_ERROR_4XX;
            default -> ExecutionOutcome.SERVER_ERROR_5XX;
        };
    }

    @Override
    public void close() {
        client.close();
    }
}
