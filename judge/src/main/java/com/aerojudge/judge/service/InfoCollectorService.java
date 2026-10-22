package com.aerojudge.judge.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;

import com.aerojudge.judge.dto.InfoJson;
import com.aerojudge.judge.dto.InfoLine;
import com.aerojudge.judge.dto.SettingDTO;
import com.aerojudge.judge.utils.INA226PowerUtils;
import com.aerojudge.judge.utils.LiPoBatteryEstimator;

import com.aerojudge.judge.utils.SettingUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InfoCollectorService {
    private static final Logger logger = LoggerFactory.getLogger(InfoCollectorService.class);

    // Board identity record in the RV-3028 user EEPROM: AJ1;<mark>;<revision>;<serial>, NUL terminated.
    // Read once at startup. The app never writes it; scripts/device_identity.sh does.
    private static final Path IDENTITY_EEPROM = Path.of("/sys/bus/nvmem/devices/rv3028_eeprom0/nvmem");
    private static final Pattern BUILD_MARK = Pattern.compile("[A-HJ-M][0-9]{2}");
    private static final Pattern BOARD_REVISION = Pattern.compile("[0-9]{1,2}\\.[0-9]{1,2}");
    private static final Pattern SERIAL = Pattern.compile("[A-Z0-9-]{1,8}");

    private String deviceSerialNumber;
    private String boardRevision;

    @Autowired
    private SettingService settingService;

    @PostConstruct
    void readBoardIdentity() {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(IDENTITY_EEPROM);
        } catch (Exception e) {
            return;
        }
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        if (end == bytes.length) {
            return;
        }
        String[] fields = new String(bytes, 0, end, StandardCharsets.US_ASCII).split(";", -1);
        if (fields.length != 4
                || !fields[0].equals("AJ1")
                || !BUILD_MARK.matcher(fields[1]).matches()
                || !BOARD_REVISION.matcher(fields[2]).matches()
                || !SERIAL.matcher(fields[3]).matches()) {
            return;
        }
        boardRevision = fields[2];
        deviceSerialNumber = fields[3];
    }

    public InfoJson collectInfo() {
        InfoJson info = new InfoJson();
        info.setAppName("AeroJudge Device");
        info.setAppVersion(com.aerojudge.judge.JudgeApplication.getAppVersion());
        info.setFiguresVersion(readPackageVersion(Path.of(SettingUtils.getApplicationConfigPath(), "figures", "package.json")));
        info.setVolumeVersion(readPackageVersion(Path.of("/var/opt/volume_service/package.json")));
        info.setDeviceSerialNumber(deviceSerialNumber);
        info.setBoardRevision(boardRevision);

        List<InfoLine> lines = new ArrayList<>();
        lines.add(getDeviceIP());
        lines.add(getWiFiStatus());
        lines.add(getBatteryStatus());
        lines.add(getScoreStatus());
        info.setInfo_lines(lines);

        return info;
    }


    // Missing metadata means an unknown installed version, not a reason to reinstall.
    private String readPackageVersion(Path path) {
        try {
            JsonElement metadata = JsonParser.parseString(Files.readString(path));
            if (!metadata.isJsonObject()) {
                return null;
            }
            JsonElement version = metadata.getAsJsonObject().get("version");
            if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isString()) {
                return null;
            }
            String value = version.getAsString();
            return value.isBlank() ? null : value;
        } catch (IOException | JsonParseException e) {
            return null;
        }
    }

    private InfoLine getDeviceIP() {
        String ip = "N/A";

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        ip = addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            ip = "unknown";
        }

        return new InfoLine("IP Address:", ip);
    }

    private InfoLine getWiFiStatus () {
        try {
            String line;
            String essid = null;
            String qualityPct = "";

            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                return new InfoLine("WiFi:", "unknown", "0%");
            } //else assume linux/Pi

            // Updated deprecated Runtime.exec() to ProcessBuilder 2025-11 DPG
            ProcessBuilder pb = new ProcessBuilder("iwconfig");
            Process process = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()));

            while ((line = reader.readLine()) != null) {
                if (line.contains("ESSID:")) {
                    int idx = line.indexOf("ESSID:");
                    essid = line.substring(idx + 6).replace("\"", "").trim();
                }
                if (line.contains("Quality=")) {
                    // Example: Quality=70/70
                    int qIdx = line.indexOf("Quality=");
                    String qualityPart = line.substring(qIdx + 8).split(" ")[0];
                    String[] parts = qualityPart.split("/");
                    if (parts.length == 2) {
                        try {
                            int val = Integer.parseInt(parts[0].trim());
                            int max = Integer.parseInt(parts[1].trim());
                            int pct = (int) ((val * 100.0) / max);
                            qualityPct = String.valueOf(pct) + "%";
                        } catch (NumberFormatException e) {
                            qualityPct = "0%";
                        }
                    }
                }
            }
            reader.close();
            return new InfoLine("WiFi:", essid, qualityPct);
        } catch (Exception e) {
            e.printStackTrace();
            return new InfoLine("WiFi:", "unknown", "0%");
        }
    }

    private InfoLine getBatteryStatus() {
        try {
            double packV = readBusVoltage();
            LiPoBatteryEstimator est = new LiPoBatteryEstimator();
            int percent = est.estimatePercentage(packV);
            int cells = est.getNumCells();

            return new InfoLine("Battery Status:", String.format("%.2fv", packV), percent + "% (" + cells + "S)");

        } catch (Exception e) {
            logger.error("Error reading battery status: " + e.getMessage());
            return new InfoLine("Battery Status:", "Error", "0%");
        }
    }

    /**
     * Lightweight battery percentage check - only gets calculated battery percentage
     * @return Battery percentage (0-100), or -1 if read fails
     */
    public int getBatteryPercent() {
        try {
            double packV = readBusVoltage();
            LiPoBatteryEstimator est = new LiPoBatteryEstimator();
            return est.estimatePercentage(packV);
        } catch (Exception e) {
            logger.warn("Battery read failed: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Read bus voltage from the I2C sensor.
     * @return Bus voltage in volts
     */
    private double readBusVoltage() throws Exception {
        Context pi4j = Pi4J.newAutoContext();
        INA226PowerUtils sensor = new INA226PowerUtils(pi4j);
        return sensor.getBusVoltage();
    }

    private InfoLine getScoreStatus () {
        // Check if score service is reachable
        try {
            SettingDTO settingDTO = settingService.getSettings();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(settingDTO.getScore_poll_timeout())).build();

            // Call a service within score to check if its available - this would best be a health check endpoint but score doesn't have one yet.
            String statusUrl = "http://" + settingDTO.getScore_host() + ":" + String.valueOf(settingDTO.getScore_http_port()) + "/scorepad/contest_info.dat";
            logger.debug("Checking score status at: " + statusUrl);
            HttpRequest statusRequest = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(settingDTO.getScore_poll_timeout()))
                .uri(URI.create(statusUrl)).build();
            HttpResponse<String> healthResponse = client.send(statusRequest, HttpResponse.BodyHandlers.ofString());
            logger.debug("Score status response code: " + healthResponse.statusCode());
            if (healthResponse.statusCode() == 200) {
                return new InfoLine("Score Status:", null, "Connected", "check_circle");
            } else {
                return new InfoLine("Score Status:", null, "Score Error", "error");
            }
        } catch (Exception e) {
            logger.error("Error checking score status: " + e.getMessage());
            return new InfoLine("Score Status:", null, "Unavailable", "error");
        }
	}

}
