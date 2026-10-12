package com.aerojudge.judge.service;

import com.aerojudge.judge.dto.SettingDTO;
import com.aerojudge.judge.utils.SettingUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Run this class in its own test JVM: PilotService's existing paths are static. */
class PilotClockIntegrationTests {
    @TempDir Path directory;

    @Test void liveHeaderPrecedesBodyAndClockFailureDoesNotBreakDownloadsOrFallback() throws Exception {
        String oldPath = SettingUtils.getApplicationConfigPath();
        SettingUtils.setApplicationConfigPath(directory.toString());
        try {
            PilotService pilots = new PilotService();
            SettingService settings = mock(SettingService.class);
            DeviceClockService clock = mock(DeviceClockService.class);
            ReflectionTestUtils.setField(pilots, "settingService", settings);
            ReflectionTestUtils.setField(pilots, "deviceClockService", clock);
            SettingDTO dto = new SettingDTO();
            dto.setScore_host("127.0.0.1");
            when(settings.getSettings()).thenReturn(dto);
            Path target = directory.resolve("pilots.dat");
            String body = "<root><pilots/></root>";

            // Different server ports prove settings are rebuilt for every call.
            for (String date : new String[]{"Mon, 12 Oct 2026 16:00:00 GMT", "bad date", null}) {
                Files.deleteIfExists(target);
                reset(clock);
                when(clock.observeScoreTime(nullable(String.class))).thenAnswer(call -> {
                    assertFalse(Files.exists(target), "Clock observation must precede file copying");
                    assertEquals(date, call.getArgument(0));
                    return false;
                });
                try (ResponseServer server = new ResponseServer(200, date, body)) {
                    dto.setScore_http_port(server.port());
                    pilots.getPilotsFileFromScore(true);
                    server.await();
                }
                assertEquals(body, Files.readString(target));
                verify(clock).observeScoreTime(date);
            }

            reset(clock);
            try (ResponseServer server = new ResponseServer(200, "Mon, 12 Oct 2026 16:00:00 GMT", body)) {
                dto.setScore_http_port(server.port());
                pilots.getPilotsFileFromScore();
                server.await();
            }
            Files.delete(target);
            try (ResponseServer server = new ResponseServer(200, "Mon, 12 Oct 2026 16:00:00 GMT", body)) {
                dto.setScore_http_port(server.port());
                assertTrue(pilots.getPilots().isEmpty());
                server.await();
            }
            verifyNoInteractions(clock);
            try (ResponseServer server = new ResponseServer(503, "Mon, 12 Oct 2026 16:00:00 GMT", "unavailable")) {
                dto.setScore_http_port(server.port());
                assertThrows(IOException.class, () -> pilots.getPilotsFileFromScore(true));
                server.await();
            }
            verifyNoInteractions(clock);
            assertEquals(body, Files.readString(target));
        } finally {
            SettingUtils.setApplicationConfigPath(oldPath);
        }
    }

    private static class ResponseServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        final CompletableFuture<Void> done;

        ResponseServer(int status, String date, String body) throws IOException {
            socket.setSoTimeout(10000);
            done = CompletableFuture.runAsync(() -> {
                try (var connection = socket.accept()) {
                    connection.setSoTimeout(10000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                    String request = input.readLine();
                    if (!"GET /scorepad/pilots.dat HTTP/1.1".equals(request)) throw new IOException("Unexpected request: " + request);
                    String line;
                    while ((line = input.readLine()) != null && !line.isEmpty()) { }
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    String headers = "HTTP/1.1 " + status + " Test\r\nConnection: close\r\nContent-Length: " + bytes.length
                            + "\r\n" + (date == null ? "" : "Date: " + date + "\r\n") + "\r\n";
                    connection.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                    connection.getOutputStream().write(bytes);
                    connection.getOutputStream().flush();
                } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
            });
        }
        int port() { return socket.getLocalPort(); }
        void await() throws Exception { done.get(10, TimeUnit.SECONDS); }
        @Override public void close() throws IOException { socket.close(); }
    }
}
