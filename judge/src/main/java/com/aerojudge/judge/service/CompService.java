package com.aerojudge.judge.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;

import com.aerojudge.judge.dto.SettingDTO;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

import com.aerojudge.judge.dto.CompDTO;
import com.aerojudge.judge.utils.SettingUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@Service
public class CompService {

    private static final Logger logger =
            LoggerFactory.getLogger(CompService.class);

    private final String COMP_FILE_NAME = SettingUtils.getApplicationConfigPath() + "/comp.json";
    private static final String COMP_INFO_DAT_PATH = SettingUtils.getApplicationConfigPath() + "/contest_info.dat";
    private static final String COMP_PREFS_DAT_PATH = SettingUtils.getApplicationConfigPath() + "/contest_prefs.dat";
    private String COMP_INFO_DAT_URL = "http://SCORE_HOST:SCORE_HTTP_PORT/scorepad/contest_info.dat";
    private String COMP_PREFS_DAT_URL = "http://SCORE_HOST:SCORE_HTTP_PORT/scorepad/contest_prefs.dat";

    private CompDTO compDTO = null;  // There is only one instance of compDTO data so why not just load it here.

    @Autowired
    private SettingService settingService;

    @Autowired
    private RoundsService roundsService;

    public void getContestInfoFileFromScore() throws MalformedURLException, IOException {
        SettingDTO settingDTO = settingService.getSettings();
        COMP_INFO_DAT_URL = COMP_INFO_DAT_URL.replace("SCORE_HOST", settingDTO.getScore_host()).replace("SCORE_HTTP_PORT", String.valueOf(settingDTO.getScore_http_port()));
        try {
            int timeout = (int)Duration.ofSeconds(settingDTO.getScore_timeout()).toMillis();
            // Updated deprecated URL, changed new URL(x) to URI.create(x).toURL() 2025-11 DPG
            FileUtils.copyURLToFile(URI.create(COMP_INFO_DAT_URL).toURL(), new File(COMP_INFO_DAT_PATH),timeout,timeout);
        } catch (Exception e) {
            try {
                // Score is probably turned off.
                logger.warn("Could not download the contest info file: " + e.getMessage());
            } catch (Exception logger_e) {
                logger_e.printStackTrace();
            }
        }
    }

    public void getContestPrefsFileFromScore() throws MalformedURLException, IOException {
        SettingDTO settingDTO = settingService.getSettings();
        COMP_PREFS_DAT_URL = COMP_PREFS_DAT_URL.replace("SCORE_HOST", settingDTO.getScore_host()).replace("SCORE_HTTP_PORT", String.valueOf(settingDTO.getScore_http_port()));
        try {
            int timeout = (int)Duration.ofSeconds(settingDTO.getScore_timeout()).toMillis();
            // Updated deprecated URL, changed new URL(x) to URI.create(x).toURL() 2025-11 DPG
            FileUtils.copyURLToFile(URI.create(COMP_PREFS_DAT_URL).toURL(), new File(COMP_PREFS_DAT_PATH),timeout,timeout);
        } catch (Exception e) {
            try {
                // Score is probably turned off.
                logger.warn("Could not download the contest preferences file: " + e.getMessage());
            } catch (Exception logger_e) {
                logger_e.printStackTrace();
            }
        }

    }

    public boolean isCurrentComp() {
        if (compDTO != null)
            return true;

        File targetFile = new File(COMP_FILE_NAME);
        if (targetFile.exists())
            try {
                this.getCompFromFile();
                return true;
            } catch (IOException e) {
                try {
                    logger.error("Could not load the comp file!!!  This is bad!");
                    e.printStackTrace();
                } catch (Throwable tAppDebug) {
                    tAppDebug.printStackTrace();
                }
            }
        return false;
    }

    public CompDTO createCompFromRequest(CompDTO compDTO) throws IOException, SAXException, ParserConfigurationException {
        // We have a new comp from the API.

        // Event loads and refreshes omit this preference; retain it before replacing the comp.
        if (compDTO.getAttempt_auto_sync_scores() == null) {
            isCurrentComp();
            compDTO.setAttempt_auto_sync_scores(this.compDTO != null
                    && Boolean.TRUE.equals(this.compDTO.getAttempt_auto_sync_scores()));
        }

        this.compDTO = compDTO;
        this.saveCompToFile();
        return this.compDTO;
    }

    public boolean saveCompToFile() throws IOException, SAXException, ParserConfigurationException {
        // We have a new comp from the API.

        if (compDTO.getComp_id() == 0)
            enrichCompWithCompInfoFromScore(compDTO);  // Get the comp name and event ID from score! if we can...

        writeCompToFile(compDTO);
        return true;
    }

    /**
     * Save comp settings locally without any Score server contact.
     * Updates both in-memory compDTO and the comp.json file.
     */
    public boolean saveCompToFileLocal() throws IOException {
        if (compDTO == null) {
            logger.warn("Cannot save local comp settings - no comp loaded");
            return false;
        }

        writeCompToFile(compDTO);
        logger.info("Saved local comp settings to file");
        return true;
    }

    /**
     * Private method to write CompDTO to file.
     */
    private void writeCompToFile(CompDTO compDTO) throws IOException {
        File newFile = new File(COMP_FILE_NAME);
        newFile.createNewFile();
        String compdtoJson = new Gson().toJson(compDTO);
        byte[] strToBytes = compdtoJson.getBytes();
        FileOutputStream outputStream = new FileOutputStream(COMP_FILE_NAME);
        outputStream.write(strToBytes);
        outputStream.close();
    }

    public CompDTO getComp() {
        return compDTO;
    }

    public CompDTO getCompFromFile() throws IOException {
        FileInputStream inputStream = new FileInputStream(COMP_FILE_NAME);
        StringBuilder resultStringBuilder = new StringBuilder();

        try (BufferedReader br
          = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
            this.compDTO = new Gson().fromJson(resultStringBuilder.toString(),CompDTO.class);

            if (this.compDTO.getAttempt_auto_sync_scores() == null) {
                this.compDTO.setAttempt_auto_sync_scores(false);
            }

            // Double check there's no null strings.
            boolean saveFile = false;
            if (this.compDTO.getComp_name() == null) {
                this.compDTO.setComp_name("Untitled Comp");
                saveFile = true;
            }

            if (this.compDTO.getScore_mode() == null) {
                this.compDTO.setScore_mode("byRound");
                saveFile = true;
            }

            logger.info("Loaded comp " + this.compDTO.getComp_name() + " (" + this.compDTO.getComp_id() + ") from file.");
            //enrichCompWithCompInfoFromScore(this.compDTO);
            if (saveFile)
                this.saveCompToFile();

        } catch( Exception e) {
            try {
                logger.error("There was an error loading the comp data..");
                e.printStackTrace();
                return null;
            } catch (Exception logger_e) {
                logger_e.printStackTrace();
                return null;
            }
        }

        return this.compDTO;
    }

    public void enrichCompWithCompInfoFromScore(CompDTO compDTO)
            throws FileNotFoundException, SAXException, IOException, ParserConfigurationException {

        getContestInfoFileFromScore();
        getContestPrefsFileFromScore();  // Not sure what to do with this one yet...

        // Probably should get sequences and pilots here as well...

        // Get Document Builder
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        // parse XML file
        DocumentBuilder db = dbf.newDocumentBuilder();

        try {
            Document doc = db.parse(new File(COMP_INFO_DAT_PATH));
            doc.getDocumentElement().normalize();
            logger.debug("Comp Info data file root Element :" + doc.getDocumentElement().getNodeName());

            Node event = doc.getElementsByTagName("event").item(0);
            Element e = (Element) event;
            compDTO.setComp_name(e.getElementsByTagName("name").item(0).getTextContent());
            compDTO.setComp_id(Integer.parseInt(e.getElementsByTagName("id").item(0).getTextContent()));
        } catch (FileNotFoundException e) {
            logger.warn("Could not get comp info.  Giving generic names for now.");
            compDTO.setComp_name("Untitled Comp");
            compDTO.setComp_id(0);
        }
        roundsService.setupRounds(compDTO.getComp_id());
        roundsService.saveRoundsToFile();
    }
}
