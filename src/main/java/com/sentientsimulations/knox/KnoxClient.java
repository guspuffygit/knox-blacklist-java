package com.sentientsimulations.knox;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sentientsimulations.knox.model.BanCheck;
import com.sentientsimulations.knox.model.BanListResponse;
import com.sentientsimulations.knox.model.BanRequest;
import com.sentientsimulations.knox.model.OrgStats;
import com.sentientsimulations.knox.model.Player;
import com.sentientsimulations.knox.model.ReportRequest;
import com.sentientsimulations.knox.model.Server;
import com.sentientsimulations.knox.model.StrikeBanRequest;
import com.sentientsimulations.knox.model.UnbanRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KnoxClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(KnoxClient.class);
    private static final String DEFAULT_BASE_URL = "https://www.knoxblacklist.com";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final Duration requestTimeout;
    private final boolean ownsHttpClient;

    private KnoxClient(Builder b) {
        this.apiKey = Objects.requireNonNull(b.apiKey, "apiKey is required");
        this.baseUrl = b.baseUrl;
        this.requestTimeout = b.requestTimeout;
        if (b.httpClient != null) {
            this.http = b.httpClient;
            this.ownsHttpClient = false;
        } else {
            // Apex (knoxblacklist.com) 301-redirects to www; HttpClient drops the
            // Authorization header across host changes, so never follow redirects.
            this.http =
                    HttpClient.newBuilder()
                            .connectTimeout(b.connectTimeout)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
            this.ownsHttpClient = true;
        }
        this.mapper = b.objectMapper != null ? b.objectMapper : defaultMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static ObjectMapper defaultMapper() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .defaultPropertyInclusion(
                        JsonInclude.Value.construct(
                                JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS))
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    // ----- Players -----

    public Player lookupPlayer(String steamId) {
        return get("/api/v1/players/" + steamId, Player.class);
    }

    public BanCheck banCheck(String serverId, String steamId) {
        return get("/api/v1/servers/" + serverId + "/ban-check/" + steamId, BanCheck.class);
    }

    public JsonNode banPlayer(String steamId, BanRequest body) {
        return post("/api/v1/players/" + steamId + "/ban", body, JsonNode.class);
    }

    public JsonNode unbanPlayer(String steamId, UnbanRequest body) {
        return post("/api/v1/players/" + steamId + "/unban", body, JsonNode.class);
    }

    public JsonNode strikeBanPlayer(String steamId, StrikeBanRequest body) {
        return post("/api/v1/players/" + steamId + "/strike-ban", body, JsonNode.class);
    }

    public JsonNode reportPlayer(String steamId, ReportRequest body) {
        return post("/api/v1/players/" + steamId + "/report", body, JsonNode.class);
    }

    // ----- Organization -----

    public OrgStats orgStats() {
        return get("/api/v1/orgs/me/stats", OrgStats.class);
    }

    public BanListResponse orgBans(int page, int limit) {
        return get("/api/v1/orgs/me/bans?page=" + page + "&limit=" + limit, BanListResponse.class);
    }

    public List<Server> orgServers() {
        return get("/api/v1/orgs/me/servers", new TypeReference<List<Server>>() {});
    }

    // ----- HTTP plumbing -----

    private <T> T get(String path, Class<T> type) {
        return send(buildGet(path), body -> readValue(body, type));
    }

    private <T> T get(String path, TypeReference<T> type) {
        return send(buildGet(path), body -> readValue(body, type));
    }

    private <T> T post(String path, Object requestBody, Class<T> type) {
        return send(buildPost(path, requestBody), body -> readValue(body, type));
    }

    private HttpRequest buildGet(String path) {
        return baseBuilder(path).GET().build();
    }

    private HttpRequest buildPost(String path, Object body) {
        byte[] json;
        try {
            json = mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new KnoxException("Failed to serialize request body", e);
        }
        return baseBuilder(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                .build();
    }

    private HttpRequest.Builder baseBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json");
    }

    private <T> T send(HttpRequest request, ResponseParser<T> parser) {
        log.debug("{} {}", request.method(), request.uri());
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new KnoxException("HTTP request failed: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KnoxException("HTTP request interrupted: " + request.uri(), e);
        }
        int status = response.statusCode();
        byte[] body = response.body();
        if (status >= 200 && status < 300) {
            try {
                return parser.parse(body);
            } catch (IOException e) {
                throw new KnoxException(
                        "Failed to deserialize response body: " + asString(body), e);
            }
        }
        if (status == 429) {
            long retryAfter = response.headers().firstValueAsLong("Retry-After").orElse(60L);
            throw new KnoxRateLimitException(asString(body), retryAfter);
        }
        throw new KnoxApiException(status, asString(body));
    }

    private <T> T readValue(byte[] body, Class<T> type) throws IOException {
        if (body.length == 0) {
            if (type == JsonNode.class) {
                @SuppressWarnings("unchecked")
                T empty = (T) mapper.nullNode();
                return empty;
            }
            return null;
        }
        return mapper.readValue(body, type);
    }

    private <T> T readValue(byte[] body, TypeReference<T> type) throws IOException {
        return mapper.readValue(body, type);
    }

    private static String asString(byte[] body) {
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        if (ownsHttpClient) {
            http.close();
        }
    }

    @FunctionalInterface
    private interface ResponseParser<T> {
        T parse(byte[] body) throws IOException;
    }

    public static final class Builder {
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private HttpClient httpClient;
        private ObjectMapper objectMapper;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public KnoxClient build() {
            return new KnoxClient(this);
        }
    }
}
