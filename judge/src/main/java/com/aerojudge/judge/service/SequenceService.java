package com.aerojudge.judge.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.aerojudge.judge.dto.CompDTO;
import com.aerojudge.judge.dto.FigureDTO;
import com.aerojudge.judge.dto.SettingDTO;
import com.aerojudge.judge.utils.SettingUtils;

@Service
public class SequenceService {

    private static final Logger logger = LoggerFactory.getLogger(SequenceService.class);

    private static final String SEQUENCES_DAT_PATH = SettingUtils.getApplicationConfigPath() + "/sequences.dat";
    private String SEQUENCES_DAT_URL = "http://SCORE_HOST:SCORE_HTTP_PORT/scorepad/sequences.dat";

    @Autowired
    private SettingService settingService;

    @Autowired
    private CompService compService;

    public void getSequenceFileFromScore() throws MalformedURLException, IOException {
        SettingDTO settingDTO = settingService.getSettings();
        int timeout = (int)Duration.ofSeconds(settingDTO.getScore_timeout()).toMillis();

        SEQUENCES_DAT_URL = SEQUENCES_DAT_URL.replace("SCORE_HOST", settingDTO.getScore_host()).replace("SCORE_HTTP_PORT", String.valueOf(settingDTO.getScore_http_port()));
        // Updated deprecated URL, changed new URL(x) to URI.create(x).toURL() 2025-11 DPG
        FileUtils.copyURLToFile(URI.create(SEQUENCES_DAT_URL).toURL(), new File(SEQUENCES_DAT_PATH),timeout,timeout);
    }

    public boolean isSequence(){
        File targetFile = new File(SEQUENCES_DAT_PATH);
        return targetFile.exists();
    }

    public  Map<String,List<FigureDTO>> getAllSequences()
            throws FileNotFoundException, SAXException, IOException, ParserConfigurationException {

        if(!isSequence()){
            getSequenceFileFromScore();
        }
        // Get Document Builder
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        // parse XML file
        DocumentBuilder db = dbf.newDocumentBuilder();

        Document doc = db.parse( new File(SEQUENCES_DAT_PATH));
        doc.getDocumentElement().normalize();
        System.out.println("Sequence file root Element :" + doc.getDocumentElement().getNodeName());

        NodeList list = doc.getElementsByTagName("sequences");
        NodeList newList = (NodeList) list.item(0);
        System.out.println(newList.getLength());
        Map<String,List<FigureDTO>> figuresMap = new HashMap<>();
        for (int temp = 0; temp < newList.getLength(); temp++) {
            Node node = newList.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String index = element.getAttribute("index");
                String type = element.getElementsByTagName("type").item(0).getTextContent();
                String _class =  "";
                try {
                     _class = element.getElementsByTagName("class").item(0).getTextContent();
                } catch (Exception e) {
                     _class = "FREESTYLE";
                }
                System.out.println(index);
                System.out.println(_class);
                // System.out.println(_class);
                List<FigureDTO> figures  = new ArrayList<>();
                Element figuresList = (Element) element.getElementsByTagName("figures").item(0);
                System.out.println(figuresList.getElementsByTagName("figure").getLength());
                for (int temp2 = 0; temp2 < figuresList.getElementsByTagName("figure").getLength(); temp2++) {
                    Node node2 = figuresList.getElementsByTagName("figure").item(temp2);
                    if (node2.getNodeType() == Node.ELEMENT_NODE) {
                        Element figureElement = (Element) node2;
                        String description = figureElement.getElementsByTagName("description").item(0).getTextContent();
                        int k_factor = Integer
                                .parseInt(figureElement.getElementsByTagName("k_factor").item(0).getTextContent());
                        String scoring = figureElement.getElementsByTagName("scoring").item(0).getTextContent();
                        figures.add(new FigureDTO((temp2+1), k_factor, description, scoring));
                    }
                }
                String key = _class.toUpperCase().trim() + ":" + type.toUpperCase().trim();
                figuresMap.put(key, figures);
            }
        }
       return figuresMap;
    }

    public  List<FigureDTO> getAllSequenceForClass(String _class, String type) throws FileNotFoundException, SAXException, IOException, ParserConfigurationException{
        Map<String,List<FigureDTO>> sequencesMap = getAllSequences();
        String key = _class.toUpperCase().trim() + ":" + type.toUpperCase().trim();
        return sequencesMap.get(key);
    }

    public boolean hasUnknownSequences() throws FileNotFoundException, SAXException, IOException, ParserConfigurationException {
        Map<String, List<FigureDTO>> sequencesMap = getAllSequences();
        for (String key : sequencesMap.keySet()) {
            if (key.toUpperCase().endsWith(":UNKNOWN")) {
                return true;
            }
        }
        return false;
    }

    public void getUnknownFiguresZip() throws IOException {
        try {
            // Get current competition
            CompDTO comp = compService.getComp();
            if (comp == null || comp.getComp_id() == 0) {
                logger.warn("No competition loaded. Cannot download unknown figures.");
                return;
            }

            // Get score settings
            SettingDTO settings = settingService.getSettings();
            if (settings == null || settings.getScore_host() == null || settings.getScore_http_port() == 0) {
                logger.warn("Score server configuration not found. Cannot download unknown figures.");
                return;
            }

            // Build the URL
            String url = String.format("http://%s:%d/aerojudge/eventdata/%d.zip",
                    settings.getScore_host(), settings.getScore_http_port(), comp.getComp_id());

            logger.info("Downloading unknown figures zip from: {}", url);

            // Create HTTP client
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(settings.getScore_timeout()))
                    .build();

            // Create request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(settings.getScore_timeout()))
                    .GET()
                    .build();

            // Send request and get response
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 404) {
                // Unknown figures zip doesn't exist - this is OK, just log and continue
                logger.info("Unknown figures zip not found at score server (404). No unknown figures available for this event.");
                return;
            }

            if (response.statusCode() != 200) {
                throw new IOException("Failed to download zip file. HTTP status: " + response.statusCode());
            }

            byte[] zipData = response.body();
            if (zipData == null || zipData.length == 0) {
                throw new IOException("Downloaded zip file is empty.");
            }

            // Target directory: {configPath}/figures/event/
            Path eventDir = Paths.get(SettingUtils.getApplicationConfigPath(), "figures", "event");

            // Ensure event directory exists
            Files.createDirectories(eventDir);

            Set<String> extractedFolders = new HashSet<>();

            try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipData))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();

                    // Security: prevent path traversal attacks
                    if (entryName.contains("..")) {
                        logger.warn("Skipping suspicious entry: {}", entryName);
                        continue;
                    }

                    // Parse the entry path
                    String[] parts = entryName.replace('\\', '/').split("/");

                    // Skip files at root level (only process folders and their contents)
                    if (parts.length < 1 || (parts.length == 1 && !entry.isDirectory())) {
                        continue;
                    }

                    String topLevelFolder = parts[0];
                    if (topLevelFolder.isEmpty()) {
                        continue;
                    }

                    extractedFolders.add(topLevelFolder);

                    // Build target path
                    Path targetPath = eventDir.resolve(entryName);

                    if (entry.isDirectory()) {
                        Files.createDirectories(targetPath);
                    } else {
                        // Ensure parent directories exist
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    zis.closeEntry();
                }
            }

            if (extractedFolders.isEmpty()) {
                throw new IOException("No folders found in ZIP file.");
            }

            logger.info("Unknown figures download complete. Extracted folders: {}", extractedFolders);

        } catch (ConnectException e) {
            logger.error("Failed to connect to score server: {}", e.getMessage());
            throw e;
        } catch (IOException e) {
            logger.error("Failed to download or extract ZIP file: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during unknown figures download: {}", e.getMessage());
            throw new IOException("Unexpected error: " + e.getMessage(), e);
        }
    }
}
