package com.aerojudge.judge.service;

import com.aerojudge.judge.dto.SettingDTO;
import com.aerojudge.judge.service.DeviceClockService.HelperResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceClockServiceTests {
    private static final String DATE = "Mon, 12 Oct 2026 16:00:00 GMT";

    static class ClockStub extends DeviceClockService {
        boolean applicable = true;
        HelperResult reply = new HelperResult(0, true, "Read", "invalid");
        IOException failure;
        final List<List<String>> commands = new ArrayList<>();

        ClockStub(SettingService settings) { super(settings); }
        @Override boolean isApplicable() { return applicable; }
        @Override HelperResult runHelper(String... arguments) throws IOException {
            commands.add(List.of(arguments));
            if (failure != null) throw failure;
            return reply;
        }
    }

    @Test void startupChecksOnlyOnceAndDismissalSurvivesRepeatedInitialization() {
        SettingService settings = mock(SettingService.class);
        ClockStub clock = new ClockStub(settings);
        clock.initializeRtcState();
        assertTrue(clock.isTimeLossWarningPending());
        clock.dismissTimeLossWarning();
        clock.initializeRtcState();
        assertFalse(clock.isTimeLossWarningPending());
        assertEquals(List.of(List.of("status")), clock.commands);
        verifyNoInteractions(settings);
    }

    @Test void onlyConfirmedInvalidTimeWarns() {
        for (HelperResult reply : List.of(
                new HelperResult(0, true, "Read", "valid"),
                new HelperResult(1, false, "Permission denied", "invalid"),
                new HelperResult(0, true, "Read", null))) {
            ClockStub clock = new ClockStub(mock(SettingService.class));
            clock.reply = reply;
            clock.initializeRtcState();
            assertFalse(clock.isTimeLossWarningPending());
        }
        ClockStub clock = new ClockStub(mock(SettingService.class));
        clock.failure = new IOException("RTC read failed");
        assertDoesNotThrow(clock::initializeRtcState);
        assertFalse(clock.isTimeLossWarningPending());
    }

    @Test void unprovisionedDoesNotReadSettingsOrRunHelper() {
        SettingService settings = mock(SettingService.class);
        ClockStub clock = new ClockStub(settings);
        clock.applicable = false;
        clock.initializeRtcState();
        assertFalse(clock.observeScoreTime(DATE));
        assertEquals(false, clock.resyncClockFromScore().get("success"));
        assertEquals(true, clock.prepareForUpdate().get("success"));
        assertTrue(clock.commands.isEmpty());
        verifyNoInteractions(settings);
    }

    @Test void malformedHeadersAndPartialWritesPreserveWarningAndDoNotThrow() {
        ClockStub clock = new ClockStub(mock(SettingService.class));
        clock.initializeRtcState();
        assertFalse(clock.observeScoreTime(null));
        assertFalse(clock.observeScoreTime("not a date"));
        assertEquals(1, clock.commands.size());
        clock.reply = new HelperResult(1, false, "RTC write failed", null);
        assertFalse(clock.observeScoreTime(DATE));
        assertTrue(clock.isTimeLossWarningPending());
        clock.failure = new IOException("Permission denied");
        assertFalse(clock.observeScoreTime(DATE));
    }

    @Test void acceptedSampleAlwaysWritesBothClocksAndClearsWarning() {
        ClockStub clock = new ClockStub(mock(SettingService.class));
        clock.initializeRtcState();
        clock.reply = new HelperResult(0, true, "Verified", null);
        assertTrue(clock.observeScoreTime(DATE));
        assertTrue(clock.observeScoreTime(DATE));
        assertFalse(clock.isTimeLossWarningPending());
        assertEquals(3, clock.commands.size());
        List<String> command = clock.commands.get(1);
        assertEquals("sync-score", command.get(0));
        assertTrue(command.get(1).matches("[0-9]+(?:\\.[0-9]+)?"));
        double sample = Double.parseDouble(command.get(1));
        long expected = Instant.parse("2026-10-12T16:00:00Z").getEpochSecond();
        assertTrue(sample >= expected && sample < expected + 5);
    }

    @Test void updateAllowsOnlyVerifiedSuccessOrSafeIncompleteRecovery() {
        ClockStub clock = new ClockStub(mock(SettingService.class));
        clock.initializeRtcState();
        for (int exit : new int[]{0, 1, 2, 3}) {
            for (boolean success : new boolean[]{true, false}) {
                clock.reply = new HelperResult(exit, success, "Outcome", null);
                assertEquals((exit == 0 && success) || (exit == 2 && !success),
                        clock.prepareForUpdate().get("success"));
            }
        }
        clock.failure = new IOException("Malformed helper result");
        assertEquals(false, clock.prepareForUpdate().get("success"));
        assertTrue(clock.isTimeLossWarningPending(), "NTP must not clear the Score warning");
    }

    @Test void malformedHelperOutputCannotAuthorizeHttps() {
        for (String output : new String[]{"", "{}", "not json", "[]",
                "{\"success\":\"false\",\"message\":\"stopped\"}",
                "{\"success\":false}", "{\"success\":false,\"message\":null}",
                "{\"success\":false,\"message\":\"\"}"}) {
            assertThrows(IOException.class, () -> DeviceClockService.parseHelperResult(2, output));
        }
        assertDoesNotThrow(() -> DeviceClockService.parseHelperResult(2,
                "{\"success\":false,\"message\":\"No NTP; stopped\"}"));
    }

    @Test void resyncUsesCurrentSettingsAndRejectsDuplicatesWhileDismissalRemainsAvailable() throws Exception {
        SettingService settings = mock(SettingService.class);
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/scorepad/pilots.dat", exchange -> {
            requestReceived.countDown();
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(200, 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
        SettingDTO dto = new SettingDTO();
        dto.setScore_host("127.0.0.1");
        dto.setScore_http_port(server.getAddress().getPort());
        when(settings.getSettings()).thenReturn(dto);
        ClockStub clock = new ClockStub(settings);
        clock.initializeRtcState();
        clock.reply = new HelperResult(0, true, "Verified", null);
        AtomicReference<Map<String, Object>> outcome = new AtomicReference<>();
        Thread worker = new Thread(() -> outcome.set(clock.resyncClockFromScore()));
        try {
            worker.start();
            assertTrue(requestReceived.await(5, TimeUnit.SECONDS));
            assertEquals(false, clock.resyncClockFromScore().get("success"));
            clock.dismissTimeLossWarning();
            assertFalse(clock.isTimeLossWarningPending());
            releaseResponse.countDown();
            worker.join(5000);
            assertFalse(worker.isAlive());
            assertEquals(true, outcome.get().get("success"));
            verify(settings, times(1)).getSettings();
        } finally {
            releaseResponse.countDown();
            server.stop(0);
            worker.join(5000);
        }
    }
}
