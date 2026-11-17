package com.fabric.fabricaiadvisor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.LongSupplier;

@Service
public class AiModelRegistry {

    private static final Logger LOG =
            LoggerFactory.getLogger(AiModelRegistry.class);

    private final ObjectMapper mapper;
    private final Path file;
    private final Function<Spec, AiModelClient> factory;
    private final LongSupplier clock;

    private final ReentrantLock reloadLock = new ReentrantLock();

    private final ThreadLocal<Selection> pinned = new ThreadLocal<>();

    private volatile Selection current;

    private byte[] lastAttempt;
    private boolean failed;
    private long nextCheck;
    private long nextRetry;

    public record Spec(
            String provider,
            String endpoint,
            String model,
            int readTimeoutSeconds) {
    }

    public record Selection(
            String revision,
            String mainId,
            String evaluationId,
            Map<String, AiModelClient> clients) {

        public Selection {
            clients = Map.copyOf(clients);
        }

        public AiModelClient main() {
            return clients.get(mainId);
        }

        public AiModelClient evaluation() {
            return evaluationId == null
                    ? null
                    : clients.get(evaluationId);
        }
    }

    @Autowired
    public AiModelRegistry(
            ObjectMapper mapper,

            @Value("${ai.models-file:}")
            String configFile,

            @Value("${ai.main.provider:llamacpp}")
            String mainProvider,

            @Value("${ai.evaluation.provider:llamacpp}")
            String evalProvider,

            @Value("${ai.main.endpoint:http://127.0.0.1:8081}")
            String mainEndpoint,

            @Value("${ai.main.model:gemma-main}")
            String mainModel,

            @Value("${ai.evaluation.endpoint:http://127.0.0.1:8082}")
            String evalEndpoint,

            @Value("${ai.evaluation.model:qwen-eval}")
            String evalModel) {

        this(
                mapper,
                configFile.isBlank()
                        ? null
                        : Path.of(configFile).toAbsolutePath(),
                defaults(
                        mainProvider,
                        evalProvider,
                        mainEndpoint,
                        mainModel,
                        evalEndpoint,
                        evalModel
                ),
                AiModelRegistry::create
        );
    }

    // Explicit legacy constructor retained for callers selecting Ollama directly.
    public AiModelRegistry(ObjectMapper mapper, String configFile, String mainEndpoint,
                           String mainModel, String evalEndpoint, String evalModel) {
        this(mapper, configFile, "ollama", "ollama", mainEndpoint, mainModel, evalEndpoint, evalModel);
    }

    AiModelRegistry(
            ObjectMapper mapper,
            Path file,
            Selection defaults,
            Function<Spec, AiModelClient> factory) {

        this(mapper, file, defaults, factory, System::nanoTime);
    }

    AiModelRegistry(
            ObjectMapper mapper,
            Path file,
            Selection defaults,
            Function<Spec, AiModelClient> factory,
            LongSupplier clock) {

        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
        this.file = file;
        this.current = Objects.requireNonNull(defaults);
        this.factory = Objects.requireNonNull(factory);
    }

    private static AiModelClient create(Spec spec) {
        return switch (spec.provider()) {
            case "llamacpp" -> new LlamaCppModelClient(
                    spec.endpoint(), spec.model(), Duration.ofSeconds(spec.readTimeoutSeconds()));
            case "ollama" -> new OllamaService(
                    spec.endpoint(),
                    spec.model(),
                    Duration.ofSeconds(spec.readTimeoutSeconds())
            );

            default -> throw new IllegalArgumentException(
                    "Provider chưa được hỗ trợ."
            );
        };
    }

    private static Selection defaults(
            String mainProvider,
            String evalProvider,
            String endpoint,
            String model,
            String evalEndpoint,
            String evalModel) {

        Map<String, AiModelClient> clients = new LinkedHashMap<>();

        clients.put(
                "env-main",
                create(new Spec(mainProvider, endpoint, model, "llamacpp".equals(mainProvider) ? 240 : 120))
        );

        if (!evalModel.isBlank()) {
            clients.put(
                    "env-evaluation",
                    create(new Spec(
                            evalProvider,
                            evalEndpoint,
                            evalModel,
                            "llamacpp".equals(evalProvider) ? 240 : 60
                    ))
            );
        }

        return new Selection(
                "environment",
                "env-main",
                evalModel.isBlank() ? null : "env-evaluation",
                clients
        );
    }

    public Selection snapshot() {
        Selection selected = pinned.get();

        if (selected != null) {
            return selected;
        }

        reloadIfChanged();
        return current;
    }

    public Scope openScope() {
        Selection previous = pinned.get();

        Selection selected = previous == null
                ? snapshot()
                : previous;

        pinned.set(selected);

        return new Scope(selected, previous);
    }

    public final class Scope implements AutoCloseable {

        private final Selection selection;
        private final Selection previous;

        private Scope(Selection selection, Selection previous) {
            this.selection = selection;
            this.previous = previous;
        }

        public Selection selection() {
            return selection;
        }

        @Override
        public void close() {
            if (previous == null) {
                pinned.remove();
            } else {
                pinned.set(previous);
            }
        }
    }

    private void reloadIfChanged() {
        if (file == null || !reloadLock.tryLock()) {
            return;
        }

        try {
            long now = clock.getAsLong();

            if (nextCheck != 0 && now - nextCheck < 0) {
                return;
            }

            nextCheck = now + 1_000_000_000L;

            if (Files.size(file) > 65536) {
                throw new IllegalArgumentException(
                        "File model quá lớn."
                );
            }

            byte[] bytes = Files.readAllBytes(file);

            if (Arrays.equals(bytes, lastAttempt)
                    && (!failed || now - nextRetry < 0)) {
                return;
            }

            lastAttempt = bytes;

            Selection candidate = parse(bytes);

            candidate.main().checkAvailability();

            if (candidate.evaluation() != null
                    && candidate.evaluation() != candidate.main()) {

                candidate.evaluation().checkAvailability();
            }

            // Chỉ áp dụng khi cả hai vai trò đã được kiểm tra.
            current = candidate;
            failed = false;

            LOG.info(
                    "AI config {}: main={}, evaluation={}",
                    candidate.revision(),
                    candidate.main().getModelName(),
                    candidate.evaluation() == null
                            ? "disabled"
                            : candidate.evaluation().getModelName()
            );

        } catch (Exception e) {
            long now = clock.getAsLong();

            if (!failed || now - nextRetry >= 0) {
                LOG.warn(
                        "Không nạp cấu hình AI mới; giữ cấu hình {} ({}: {}).",
                        current.revision(), e.getClass().getSimpleName(),
                        Objects.toString(e.getMessage(), "").replaceAll("[\\r\\n]", " ").substring(0,
                                Math.min(200, Objects.toString(e.getMessage(), "").replaceAll("[\\r\\n]", " ").length()))
                );
            }

            failed = true;
            nextRetry = now + 10_000_000_000L;

        } finally {
            reloadLock.unlock();
        }
    }

    private Selection parse(byte[] bytes) {
        JsonNode root = mapper.readTree(bytes);

        check(root != null && root.isObject());
        fields(root, Set.of("revision", "models", "active"));

        String revision = text(root.path("revision"));
        check(revision.matches("[A-Za-z0-9._-]{1,64}"));

        JsonNode models = root.path("models");

        check(models.isObject()
                && models.size() > 0
                && models.size() <= 16);

        Map<String, AiModelClient> clients = new LinkedHashMap<>();

        for (var entry : models.properties()) {
            String id = entry.getKey();
            check(id.matches("[A-Za-z0-9._-]{1,64}"));

            JsonNode value = entry.getValue();

            check(value.isObject());

            fields(
                    value,
                    Set.of(
                            "provider",
                            "endpoint",
                            "model",
                            "readTimeoutSeconds"
                    )
            );

            String provider = text(value.path("provider"));
            check(Set.of("ollama", "llamacpp").contains(provider));

            String endpoint = text(value.path("endpoint"));
            URI uri = URI.create(endpoint);

            check(
                    Set.of("http", "https").contains(uri.getScheme())
                            && uri.getHost() != null
                            && uri.getUserInfo() == null
                            && uri.getQuery() == null
                            && uri.getFragment() == null
            );

            String model = text(value.path("model"));
            check(model.length() <= 200);

            JsonNode timeout = value.path("readTimeoutSeconds");

            check(timeout.isIntegralNumber()
                    && timeout.canConvertToInt());

            int seconds = timeout.intValue();
            check(seconds >= 1 && seconds <= 300);

            clients.put(
                    id,
                    factory.apply(new Spec(
                            provider,
                            endpoint,
                            model,
                            seconds
                    ))
            );
        }

        JsonNode active = root.path("active");

        check(active.isObject());
        fields(active, Set.of("main", "evaluation"));

        String main = text(active.path("main"));

        JsonNode evaluation = active.path("evaluation");

        check(evaluation.isNull() || evaluation.isTextual());

        String eval = evaluation.isNull()
                ? null
                : text(evaluation);

        check(clients.containsKey(main)
                && (eval == null || clients.containsKey(eval)));

        return new Selection(revision, main, eval, clients);
    }

    private static void fields(
            JsonNode node,
            Set<String> allowed) {

        for (var field : node.properties()) {
            check(allowed.contains(field.getKey()));
        }
    }

    private static String text(JsonNode node) {
        check(node.isTextual() && !node.asText().isBlank());
        return node.asText().trim();
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException(
                    "Cấu hình model không hợp lệ."
            );
        }
    }
}