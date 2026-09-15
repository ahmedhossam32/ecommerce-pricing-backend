package com.ecommerce.client;

import com.ecommerce.config.RestTemplateConfig;
import com.ecommerce.dto.request.MLRequest;
import com.ecommerce.dto.response.MLResponse;
import com.ecommerce.exception.PricingException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Behavioral proof for the RestTemplateConfig/MLClient timeout fix.
 *
 * No Spring context is started — MLClient has no dependency on anything Spring-managed
 * beyond the RestTemplate itself, so these tests build the RestTemplate the exact same way
 * production does (by calling the real RestTemplateConfig.restTemplate(...) bean method
 * directly, not re-implementing its logic) and construct MLClient as a plain object,
 * pointing it at a local JDK HttpServer instead of a real ML service.
 *
 * No new test dependency was added: WireMock/OkHttp MockWebServer aren't already on the
 * classpath (checked pom.xml first, per the task), and the JDK's built-in
 * com.sun.net.httpserver.HttpServer is enough to accept-and-delay or accept-and-respond,
 * which is all these tests need — consistent with this project's existing preference for
 * dependency-free unit tests (Mockito-only, no Spring context) elsewhere in src/test.
 */
@DisplayName("MLClient — timeout fix (behavioral verification)")
class MLClientTimeoutTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(int delayMillis, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/predict", exchange -> {
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Builds a RestTemplate via the real production bean method — not a reimplementation of it. */
    private RestTemplate realRestTemplate(long connectTimeoutMs, long readTimeoutMs) {
        return new RestTemplateConfig().restTemplate(new RestTemplateBuilder(), connectTimeoutMs, readTimeoutMs);
    }

    private MLClient mlClientFor(RestTemplate restTemplate, String url) {
        MLClient client = new MLClient(restTemplate);
        ReflectionTestUtils.setField(client, "mlServiceUrl", url);
        return client;
    }

    // ── Test 1 — read timeout actually fires ────────────────────────────────
    @Test
    @DisplayName("Test 1: server accepts but hangs past the read timeout -> bounded failure, wrapped as PricingException")
    void predict_readTimeoutFires_wrappedAsPricingExceptionWithinBound() throws IOException {
        String url = startServer(2000, "{\"predicted_price\": 42.0}"); // 2s server delay
        MLClient mlClient = mlClientFor(realRestTemplate(500, 1000), url); // connect=500ms, read=1000ms

        long start = System.nanoTime();
        Throwable thrown = catchThrowable(() -> mlClient.predict(MLRequest.builder().build()));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("[Test 1] elapsed=" + elapsedMs + "ms message=\"" + thrown.getMessage() + "\"");

        assertThat(thrown).isInstanceOf(PricingException.class);
        assertThat(elapsedMs)
                .as("must fail well before the 2s server delay elapses, bounded by the 1s read timeout + margin")
                .isLessThan(1500);
        assertThat(thrown.getMessage())
                .as("message should reveal a timeout, not just a generic 'unavailable'")
                .containsIgnoringCase("timed out");
    }

    // ── Test 2 — connect timeout fires on an unreachable host ───────────────
    @Test
    @DisplayName("Test 2: unreachable host -> bounded failure, nowhere near an unbounded OS-level TCP timeout")
    void predict_connectTimeoutFires_onUnreachableHost() {
        // RFC 5737 TEST-NET-1 — reserved, non-routable, guaranteed not to resolve to a real host.
        MLClient mlClient = mlClientFor(realRestTemplate(500, 1000), "http://192.0.2.1:8000");

        long start = System.nanoTime();
        Throwable thrown = catchThrowable(() -> mlClient.predict(MLRequest.builder().build()));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("[Test 2] elapsed=" + elapsedMs + "ms message=\"" + thrown.getMessage() + "\"");

        assertThat(thrown).isInstanceOf(PricingException.class);
        assertThat(elapsedMs)
                .as("must fail in bounded time -- an unconfigured RestTemplate could hang 60s+ here")
                .isLessThan(3000);
    }

    // ── Test 3 — happy path unaffected ───────────────────────────────────────
    @Test
    @DisplayName("Test 3: fast valid response still returns the correct MLResponse with timeouts configured")
    void predict_happyPath_stillWorksWithTimeoutsConfigured() throws IOException {
        String url = startServer(0, "{\"predicted_price\": 123.45}");
        MLClient mlClient = mlClientFor(realRestTemplate(3000, 10000), url); // production defaults

        MLResponse response = mlClient.predict(MLRequest.builder().build());

        assertThat(response).isNotNull();
        assertThat(response.getPredictedPrice()).isEqualTo(123.45);
    }

    // ── Test 4 — existing connection-refused path unaffected ────────────────
    @Test
    @DisplayName("Test 4: connection refused (closed local port) still wraps as PricingException, as before this change")
    void predict_connectionRefused_stillWrappedAsPricingException() throws IOException {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        } // port is closed again as soon as this block exits

        MLClient mlClient = mlClientFor(realRestTemplate(3000, 10000), "http://localhost:" + closedPort);

        Throwable thrown = catchThrowable(() -> mlClient.predict(MLRequest.builder().build()));

        System.out.println("[Test 4] message=\"" + thrown.getMessage() + "\"");

        assertThat(thrown).isInstanceOf(PricingException.class);
    }
}
