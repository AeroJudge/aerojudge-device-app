package com.aerojudge.judge.service;

import com.aerojudge.judge.dto.SettingDTO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local clock operations are optional; judging never depends on their success. */
@Service
public class DeviceClockService {
    private static final Logger logger = LoggerFactory.getLogger(DeviceClockService.class);
    private static final Path HELPER = Path.of("/usr/local/sbin/aerojudge-clock");
    private static final Path RTC_NAME = Path.of("/sys/class/rtc/rtc0/name");
    private static final ObjectMapper HELPER_JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final SettingService settingService;
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean warningPending = new AtomicBoolean();
    private final AtomicBoolean resyncRunning = new AtomicBoolean();

    public DeviceClockService(SettingService settingService) {
        this.settingService = settingService;
    }

    @PostConstruct
    public void initializeRtcState() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        try {
            if (isApplicable()) {
                HelperResult result = runHelper("status");
                if (result.exitCode() == 0 && result.success()) {
                    warningPending.set("invalid".equals(result.state()));
                } else {
                    logger.warn("RTC startup check failed: {}", result.message());
                }
            }
        } catch (Exception e) {
            logger.warn("RTC startup check failed; time loss is not confirmed: {}", e.getMessage());
        }
    }

    public boolean observeScoreTime(String httpDate) {
        return Boolean.TRUE.equals(synchronizeScoreTime(httpDate).get("success"));
    }

    private Map<String, Object> synchronizeScoreTime(String httpDate) {
        long receivedAt = System.nanoTime();
        try {
            if (!isApplicable()) {
                return result(false, "Device clock support is not available.");
            }
            if (httpDate == null || httpDate.isBlank()) {
                throw new IOException("Score response has no Date header.");
            }
            long epoch = ZonedDateTime.parse(httpDate, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().getEpochSecond();
            // Advance this live sample only by local processing time. Never queue it.
            double advancingEpoch = epoch + (System.nanoTime() - receivedAt) / 1_000_000_000.0;
            HelperResult reply = runHelper("sync-score", BigDecimal.valueOf(advancingEpoch).toPlainString());
            if (reply.exitCode() == 0 && reply.success()) {
                warningPending.set(false);
                return result(true, "Clock synchronized with Score");
            }
            logger.warn("Score clock synchronization failed: {}", reply.message());
            return result(false, reply.message());
        } catch (Exception e) {
            logger.warn("Score clock synchronization failed: {}", e.getMessage());
            return result(false, "Clock synchronization failed: " + e.getMessage());
        }
    }

    public Map<String, Object> resyncClockFromScore() {
        if (!resyncRunning.compareAndSet(false, true)) {
            return result(false, "Clock synchronization is already running. Please retry shortly.");
        }
        HttpURLConnection connection = null;
        try {
            if (!isApplicable()) {
                return result(false, "Device clock support is not available.");
            }
            SettingDTO settings = settingService.getSettings();
            int timeout = Math.toIntExact(Duration.ofSeconds(settings.getScore_timeout()).toMillis());
            connection = (HttpURLConnection) URI.create("http://" + settings.getScore_host() + ":"
                    + settings.getScore_http_port() + "/scorepad/pilots.dat").toURL().openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setUseCaches(false);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Score returned HTTP " + status + ".");
            }
            // This GET supplies only its header: no pilot files, parsing, or caches.
            try (InputStream ignored = connection.getInputStream()) {
                return synchronizeScoreTime(connection.getHeaderField("Date"));
            }
        } catch (Exception e) {
            logger.warn("Clock resync request failed: {}", e.getMessage());
            return result(false, "Unable to resync clock: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            resyncRunning.set(false);
        }
    }

    public boolean isTimeLossWarningPending() {
        return warningPending.get();
    }

    public void dismissTimeLossWarning() {
        warningPending.set(false);
    }

    public Map<String, Object> prepareForUpdate() {
        try {
            if (!isApplicable()) {
                return result(true, "Device clock provisioning is not active.");
            }
            HelperResult reply = runHelper("sync-internet");
            if (reply.exitCode() == 0 && reply.success()) {
                return result(true, reply.message());
            }
            logger.warn("Internet clock recovery did not complete: {}", reply.message());
            // Exit 2 is meaningful only with a well-formed helper failure result.
            return result(reply.exitCode() == 2 && !reply.success(), reply.message());
        } catch (Exception e) {
            logger.warn("Internet clock recovery failed: {}", e.getMessage());
            return result(false, "Clock recovery failed: " + e.getMessage());
        }
    }

    boolean isApplicable() throws IOException {
        return Files.isExecutable(HELPER) && Files.exists(RTC_NAME)
                && Files.readString(RTC_NAME).trim().matches("rtc-rv3028(?:\\s.*)?");
    }

    record HelperResult(int exitCode, boolean success, String message, String state) { }

    HelperResult runHelper(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("sudo", "-n", HELPER.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        int limit = "sync-internet".equals(arguments[0]) ? 60 : 5;
        try {
            if (!process.waitFor(limit, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                throw new IOException("Clock helper timed out; completion and cleanup are not confirmed.");
            }
            // The helper emits one small JSON object, never subprocess output.
            String output = new String(process.getInputStream().readNBytes(8193), StandardCharsets.UTF_8);
            if (output.length() > 8192) {
                throw new IOException("Clock helper returned an oversized result.");
            }
            HelperResult reply = parseHelperResult(process.exitValue(), output);
            if ("status".equals(arguments[0]) && reply.exitCode() == 0 && reply.success()
                    && !"valid".equals(reply.state()) && !"invalid".equals(reply.state())) {
                throw new IOException("Clock helper returned no valid RTC state.");
            }
            return reply;
        } catch (InterruptedException e) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } finally {
                Thread.currentThread().interrupt();
            }
            throw e;
        } finally {
            process.getInputStream().close();
            process.getOutputStream().close();
        }
    }

    static HelperResult parseHelperResult(int exitCode, String output) throws IOException {
        try {
            JsonNode json = HELPER_JSON.readTree(output);
            if (json == null || !json.isObject() || !json.path("success").isBoolean()
                    || !json.path("message").isTextual() || json.path("message").asText().isBlank()) {
                throw new IllegalArgumentException("Missing success/message fields");
            }
            String state = json.path("state").isTextual() ? json.get("state").asText() : null;
            return new HelperResult(exitCode, json.get("success").asBoolean(),
                    json.get("message").asText(), state);
        } catch (RuntimeException | IOException e) {
            throw new IOException("Clock helper returned an invalid result; cleanup is not confirmed.", e);
        }
    }

    private static Map<String, Object> result(boolean success, String message) {
        return Map.of("success", success, "message", message);
    }
}
