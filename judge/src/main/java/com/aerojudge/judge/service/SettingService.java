package com.aerojudge.judge.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.zip.ZipOutputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.google.gson.Gson;
import com.aerojudge.judge.dto.SettingDTO;
import com.aerojudge.judge.utils.SettingUtils;

@Service
public class SettingService {

    private static final Logger logger = LoggerFactory.getLogger(SettingService.class);
    private final String PILOT_SCORE_PATH = SettingUtils.getApplicationConfigPath() + "/pilots/scores";

    private final String SETTINGS_FILE_NAME = SettingUtils.getApplicationConfigPath() + "/settings.json";
    private boolean firstRun = true;

    public boolean isSettings() {
        File targetFile = new File(SETTINGS_FILE_NAME);
        logger.debug("Exists: " + SETTINGS_FILE_NAME + " - " + targetFile.exists());
        return targetFile.exists();
    }

    public boolean isFirstRun() {
        return firstRun;
    }

    public SettingService setFirstRun(boolean firstRun) {
        this.firstRun = firstRun;
        return this;
    }

    public SettingDTO updateSettings(SettingDTO settingDTO) throws IOException {
        File newFile = new File(SETTINGS_FILE_NAME);
        newFile.createNewFile();
        String compdtoJson = new Gson().toJson(settingDTO);
        byte[] strToBytes = compdtoJson.getBytes();

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("windows")) {
            // On Windows (development), write directly to the settings file
            FileOutputStream outputStream = new FileOutputStream(SETTINGS_FILE_NAME);
            outputStream.write(strToBytes);
            outputStream.close();
        } else {
            // On Linux (Pi device), use temporary file since target file is owned by root
            FileOutputStream outputStream = new FileOutputStream(SETTINGS_FILE_NAME.replace(".json", "_tmp.json"));
            outputStream.write(strToBytes);
            outputStream.close();

            // Now replace the original file with the temporary file
            moveTemporarySettingsFile();
        }

        // get settings file
        return settingDTO;
    }

    public SettingDTO createSettings() throws IOException {
        SettingDTO settingDTO = new SettingDTO();
        File newFile = new File(SETTINGS_FILE_NAME);
        try {
            newFile.createNewFile();
        } catch (IOException e) {
            logger.error("Could not create " + SETTINGS_FILE_NAME);
            throw (e);
        }
        String compdtoJson = new Gson().toJson(settingDTO);
        byte[] strToBytes = compdtoJson.getBytes();
        FileOutputStream outputStream = new FileOutputStream(SETTINGS_FILE_NAME);
        outputStream.write(strToBytes);
        outputStream.close();
        // get settings file
        return settingDTO;
    }

    public SettingDTO getSettings() throws IOException {
        if (isSettings()) {
            FileInputStream inputStream = new FileInputStream(SETTINGS_FILE_NAME);
            StringBuilder resultStringBuilder = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = br.readLine()) != null) {
                    resultStringBuilder.append(line).append("\n");
                }
            }
            return new Gson().fromJson(resultStringBuilder.toString(), SettingDTO.class);
        } else {
            return createSettings();
        }
    }

    public void backupAllFiles() throws IOException{
        String sourceFile = SettingUtils.getApplicationConfigPath() + "/pilots";
        String date =  new SimpleDateFormat("yyyyMMdd-HHmmss").format(Calendar.getInstance().getTime());
        FileOutputStream fos = new FileOutputStream(SettingUtils.getApplicationConfigPath() + "/" + "judge.backup."+ date + ".zip");
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        File fileToZip = new File(sourceFile);
        SettingUtils.zipFile(fileToZip, fileToZip.getName(), zipOut);
        zipOut.close();
        fos.close();

        //remove pilot data
        Files.walk(Paths.get(PILOT_SCORE_PATH))
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .forEach(File::delete);
    }

    public void moveTemporarySettingsFile() throws IOException {
        String commandString = String.format("sudo cp %s %s", SETTINGS_FILE_NAME.replace(".json", "_tmp.json"), SETTINGS_FILE_NAME);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("sh", "-c", commandString);
    
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
    
            if (exitCode == 0) {
                logger.info("Temporary settings file successfully moved to settings.json");
            } else {
                logger.error("Failed to move temporary settings file. Exit code: " + exitCode);
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        logger.error(errorLine);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
            logger.error("Process was interrupted while moving the temporary settings file", e);
        }
    }
}
