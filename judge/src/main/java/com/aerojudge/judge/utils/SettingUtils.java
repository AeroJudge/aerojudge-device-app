package com.aerojudge.judge.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

@Component
public class SettingUtils {

    public static final String DEFAULT_APPLICATION_CONFIG_PATH = "/var/opt/judge/";
    // cannot be same as config path
    private static String APPLICATION_CONFIG_PATH = DEFAULT_APPLICATION_CONFIG_PATH;

    public static String getApplicationConfigPath() {
        return (APPLICATION_CONFIG_PATH);
    }

    public static String setApplicationConfigPath(String appConfigPath) {
        APPLICATION_CONFIG_PATH = appConfigPath;
        return (APPLICATION_CONFIG_PATH);
    }

    // Missing metadata means an unknown installed version, not a reason to reinstall.
    public static String readPackageVersion(Path path) {
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

    public static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {

                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }
}
