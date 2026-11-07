package com.aerojudge.judge.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeviceClockService {
    public enum ClockState { NONE, VALID, INVALID, UNAVAILABLE }
    public enum ClockIntent { NEW_EVENT, REFRESH, NONE }
    public record ClockCapture(String timestamp, boolean trusted) {}
    public record ClockSyncResult(boolean success, String message) {}
    private record HelperResult(boolean success, String output, String reason) {}

    private static final Logger logger = LoggerFactory.getLogger(DeviceClockService.class);
    private final SettingService settings;
    private final ReentrantLock helperLock = new ReentrantLock();
    private Process liveProcess;
    private volatile ClockState state = ClockState.UNAVAILABLE;
    private volatile boolean warningPending;

    public DeviceClockService(SettingService settings) { this.settings = settings; }

    @PostConstruct
    public void initialize() {
        helperLock.lock();
        try {
            HelperResult result = invokeHelper("status");
            state = result.success() ? switch (result.output()) {
                case "none" -> ClockState.NONE;
                case "valid" -> ClockState.VALID;
                case "invalid" -> ClockState.INVALID;
                default -> ClockState.UNAVAILABLE;
            } : ClockState.UNAVAILABLE;
            warningPending = state == ClockState.INVALID || state == ClockState.UNAVAILABLE;
        } finally { helperLock.unlock(); }
    }

    public ClockState getState() { return state; }
    public boolean isTrusted() { return state == ClockState.VALID; }
    public boolean isBootWarningPending() { return warningPending; }
    public void dismissBootWarning() { warningPending = false; }
    public ClockCapture captureTimestamp() {
        return new ClockCapture(now().truncatedTo(ChronoUnit.SECONDS).toString(), isTrusted());
    }

    public void applyScoreDate(String dateHeader, ClockIntent intent) {
        if (intent == ClockIntent.NONE || state == ClockState.NONE) return;
        synchronizeSample(dateHeader, intent, nanoTime());
    }

    public ClockSyncResult resyncFromScore() {
        if (state == ClockState.NONE) return failure("No supported RTC is installed.");
        HttpURLConnection connection = null;
        try {
            var configured = settings.getSettings();
            int timeout = Math.toIntExact(TimeUnit.SECONDS.toMillis(configured.getScore_timeout()));
            connection = (HttpURLConnection) URI.create("http://" + configured.getScore_host() + ":"
                    + configured.getScore_http_port() + "/scorepad/pilots.dat").toURL().openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            // Read headers from the same endpoint, without saving or parsing its body.
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return failure("Score returned HTTP " + status + ".");
            String date = connection.getHeaderField("Date");
            long received = nanoTime();
            return synchronizeSample(date, ClockIntent.NEW_EVENT, received);
        } catch (Exception e) {
            return failure("Could not obtain Score time: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private ClockSyncResult synchronizeSample(String date, ClockIntent intent, long received) {
        final Instant sample;
        try {
            sample = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception e) {
            return failure("Score did not provide a usable Date header.");
        }
        if (!helperLock.tryLock()) return failure("A clock operation is already running.");
        try {
            double epoch = sample.getEpochSecond() + (nanoTime() - received) / 1_000_000_000.0;
            Instant current = now();
            double currentEpoch = current.getEpochSecond() + current.getNano() / 1_000_000_000.0;
            if (intent == ClockIntent.REFRESH && isTrusted() && Math.abs(currentEpoch - epoch) <= 5)
                return new ClockSyncResult(true, "Device time is trusted.");
            // A required correction remains untrusted until the helper verifies both clocks.
            state = ClockState.UNAVAILABLE;
            HelperResult result = invokeHelper("set", java.math.BigDecimal.valueOf(epoch).toPlainString());
            if (!result.success() || !result.output().startsWith("ok "))
                return failure(result.success() ? "Unexpected clock helper response." : result.reason());
            logger.info("Clock synchronized; helper readback: {}", result.output());
            state = ClockState.VALID;
            warningPending = false;
            return new ClockSyncResult(true, "Device time synchronized with Score.");
        } finally { helperLock.unlock(); }
    }

    private ClockSyncResult failure(String reason) {
        logger.warn("Clock synchronization failed: {}", reason);
        return new ClockSyncResult(false, "Clock synchronization failed: " + reason);
    }

    // Caller holds helperLock. Keep timed-out processes until they actually exit:
    // judge cannot reliably terminate a privileged helper, and late success is ignored.
    private HelperResult invokeHelper(String... arguments) {
        if (liveProcess != null && liveProcess.isAlive())
            return new HelperResult(false, "", "The previous clock helper is still running.");
        try {
            liveProcess = startHelper(arguments);
            if (!liveProcess.waitFor(8, TimeUnit.SECONDS)) {
                logger.warn("Clock helper exceeded its eight-second wait.");
                return new HelperResult(false, "", "Clock helper timed out.");
            }
            String output = new String(liveProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String error = new String(liveProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return new HelperResult(liveProcess.exitValue() == 0, output,
                    error.isEmpty() ? "Clock helper failed (exit " + liveProcess.exitValue() + ")." : error);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HelperResult(false, "", "Clock helper wait interrupted.");
        } catch (IOException e) {
            logger.warn("Clock helper could not run: {}", e.getMessage());
            return new HelperResult(false, "", e.getMessage());
        }
    }

    protected Process startHelper(String... arguments) throws IOException {
        var command = new java.util.ArrayList<String>();
        command.add("sudo"); command.add("-n"); command.add("/usr/local/sbin/aerojudge-clock");
        command.addAll(java.util.List.of(arguments));
        return new ProcessBuilder(command).start();
    }
    protected Instant now() { return Instant.now(); }
    protected long nanoTime() { return System.nanoTime(); }
}
