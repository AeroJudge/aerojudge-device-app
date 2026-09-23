package com.aerojudge.judge.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.gson.Gson;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import com.aerojudge.judge.dto.CompDTO;
import com.aerojudge.judge.dto.FigureUploadDTO;
import com.aerojudge.judge.dto.FiguresUploadDTO;
import com.aerojudge.judge.dto.FlightUploadDTO;
import com.aerojudge.judge.dto.FlightsUploadDTO;
import com.aerojudge.judge.dto.PScore;
import com.aerojudge.judge.dto.Pilot;
import com.aerojudge.judge.dto.PilotScoreDTO;
import com.aerojudge.judge.dto.PilotScores;
import com.aerojudge.judge.dto.SettingDTO;
import com.aerojudge.judge.utils.SettingUtils;

import javax.xml.parsers.ParserConfigurationException;

@Service
public class PilotService {
    private static final Logger logger =
            LoggerFactory.getLogger(PilotService.class);

    private static final String PILOT_SCORE_DIR = SettingUtils.getApplicationConfigPath() + "/pilots/scores/";
    private static final String PILOT_DAT_PATH = SettingUtils.getApplicationConfigPath() + "/pilots.dat";
    private String PILOT_DAT_URL = "http://SCORE_HOST:SCORE_HTTP_PORT/scorepad/pilots.dat";
    private String SCORE_UPLOAD_URL = "http://SCORE_HOST:SCORE_HTTP_PORT/scorepadupload/";
    @Autowired
    private CompService compService;
    @Autowired
    private SettingService settingService;

    public void getPilotsFileFromScore() throws MalformedURLException, IOException {
        SettingDTO settingDTO = settingService.getSettings();
        int timeout = (int)Duration.ofSeconds(settingDTO.getScore_timeout()).toMillis();
        PILOT_DAT_URL = PILOT_DAT_URL.replace("SCORE_HOST", settingDTO.getScore_host()).replace("SCORE_HTTP_PORT", String.valueOf(settingDTO.getScore_http_port()));
        // Updated deprecated URL, changed new URL(x) to URI.create(x).toURL() 2025-11 DPG
        FileUtils.copyURLToFile(URI.create(PILOT_DAT_URL).toURL(), new File(PILOT_DAT_PATH),timeout,timeout);
    }

    public boolean isPilots(){
        File targetFile = new File(PILOT_DAT_PATH);
        return targetFile.exists();
    }

    public List<Pilot> getPilots() throws ParserConfigurationException, SAXException, IOException {
        return getPilots(false);
    }

    public List<Pilot> getPilots(boolean activeOnly) throws ParserConfigurationException, SAXException, IOException {
        if(!isPilots()){
            getPilotsFileFromScore();
        }
        // Get Document Builder
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        // parse XML file
        DocumentBuilder db = dbf.newDocumentBuilder();

        Document doc = db.parse(new File(PILOT_DAT_PATH));
        // optional, but recommended
        // http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
        doc.getDocumentElement().normalize();
        logger.debug ("Pilot file root Element :" + doc.getDocumentElement().getNodeName());
        // get <staff>
        NodeList list = doc.getElementsByTagName("pilots");
        NodeList newList = (NodeList) list.item(0);

        logger.debug ("Pilot count: " + newList.getLength());
        List<Pilot> pilots = new ArrayList<>();
        for (int temp = 0; temp < newList.getLength(); temp++) {
            Node node = newList.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                int index = Integer.parseInt(element.getAttribute("index"));
                String primary_id = element.getElementsByTagName("primary_id").item(0).getTextContent();
                String secondary_id = "";
                int comp_id = 0;
                if (element.getElementsByTagName("secondary_id").item(0).hasChildNodes() == true && 
                    element.getElementsByTagName("secondary_id").item(0).getTextContent().length() > 0) {
                    secondary_id = element.getElementsByTagName("secondary_id").item(0).getTextContent();
                }
                if (element.getElementsByTagName("comp_id").item(0).hasChildNodes() == true && 
                    element.getElementsByTagName("comp_id").item(0).getTextContent().length() > 0) {
                    try {
                        comp_id = Integer.parseInt(element.getElementsByTagName("comp_id").item(0).getTextContent());
                    } catch (NumberFormatException e) {
                        comp_id = index; // fallback to index if comp_id is not a number
                    }
                }
                String name = element.getElementsByTagName("name").item(0).getTextContent();
                String addr1 = element.getElementsByTagName("addr1").item(0).getTextContent();
                String addr2 = element.getElementsByTagName("addr2").item(0).getTextContent();
                String airplane = element.getElementsByTagName("airplane").item(0).getTextContent();
                Boolean missing_pilot_panel = Boolean
                        .parseBoolean(element.getElementsByTagName("airplane").item(0).getTextContent());
                String comments = element.getElementsByTagName("comments").item(0).getTextContent();
                Boolean active = Boolean.parseBoolean(element.getElementsByTagName("active").item(0).getTextContent());
                // Missing or empty dropped elements evaluate to false.
                Node droppedNode = element.getElementsByTagName("dropped").item(0);
                boolean dropped = droppedNode != null
                        && Boolean.parseBoolean(droppedNode.getTextContent());
                Boolean freestyle = Boolean
                        .parseBoolean(element.getElementsByTagName("freestyle").item(0).getTextContent());
                Boolean spread_spectrum = Boolean
                        .parseBoolean(element.getElementsByTagName("spread_spectrum").item(0).getTextContent());
                int frequency = Integer.parseInt(element.getElementsByTagName("frequency").item(0).getTextContent());

                NodeList classesList = doc.getElementsByTagName("classes");
                Element class_element = (Element) classesList.item(index);
                String _class = class_element.getElementsByTagName("class").item(0).getTextContent();
                Pilot pilot = new Pilot(freestyle, comments, addr2, addr1, _class, index, active, comp_id, frequency,
                        spread_spectrum, secondary_id, airplane, name, missing_pilot_panel, primary_id);
                // Active-pilot lists exclude pilots dropped in Score.
                if (!activeOnly || (Boolean.TRUE.equals(active) && !dropped)) {
                    pilots.add(pilot);
                }
                logger.debug(new Gson().toJson(pilot));
            }
        }
        
        return pilots;
    }

    public Pilot getPilot(String pilot_id) throws ParserConfigurationException, SAXException, IOException {
        List<Pilot> pilots = getPilots();
        return pilots.stream().filter(pilot -> pilot.getPrimary_id().equals(pilot_id)).findFirst().orElse(null);
    }

    public void setupPilotScores() throws ParserConfigurationException, SAXException, IOException {
        List<Pilot> pilots = getPilots();
        pilots.parallelStream().forEach(pilot -> {
            createPilotScoreFile(pilot);
        });
    }

    public void createPilotScoreFile(Pilot pilot) {
        try {
            String filepath = PILOT_SCORE_DIR + pilot.getPrimary_id() + ".json";
            File pilotScoreFile = new File(filepath);
            pilotScoreFile.getParentFile().mkdirs();
            boolean createResult = pilotScoreFile.createNewFile();
            if (createResult)
                logger.debug("File created: " + filepath);
            else
                logger.warn("File exists: " + filepath);

            FileWriter fw = new FileWriter(pilotScoreFile.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);

            PilotScores pilotScores = new PilotScores(pilot.getName(), pilot.getPrimary_id(), pilot.getClassString(), settingService.getSettings().getJudge_id());
            bw.write(new Gson().toJson(pilotScores));
            bw.close();
        } catch (Exception e) {
            logger.error("Could not create pilot file for pilot " + pilot.getName() + " (" + pilot.getPrimary_id() + ").");
            logger.error("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public PilotScores getPilotScores(Pilot pilot) throws IOException {
        String filepath = PILOT_SCORE_DIR + pilot.getPrimary_id() + ".json";
        File pilotScoreFile = new File(filepath);
        if (!pilotScoreFile.exists()) {
            createPilotScoreFile(pilot);
        }
        Path path = Paths.get(filepath);
        String pilotScoresJson = Files.readAllLines(path).get(0);
        return new Gson().fromJson(pilotScoresJson, PilotScores.class);
    }

    public PilotScores savePilotScoresToFile(PilotScores scores) throws IOException {
        String filepath = PILOT_SCORE_DIR + scores.getPrimary_id() + ".json";
        File pilotScoreFile = new File(filepath);
        if (!pilotScoreFile.exists()) {
            try {
                createPilotScoreFile(getPilot(scores.getPrimary_id()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //
        FileWriter fw = new FileWriter(pilotScoreFile.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(new Gson().toJson(scores));
        bw.close();
        return scores;
    }

    public PilotScores submitScore(PilotScoreDTO pilotScoreDTO)
            throws ParserConfigurationException, SAXException, IOException {
        Pilot pilot = getPilot(pilotScoreDTO.getPrimary_id());
        PilotScores pilotScores = getPilotScores(pilot);
        PScore newScore = new PScore(pilotScoreDTO.getRound(), pilotScoreDTO.getSequence(), pilotScoreDTO.getScores(),pilotScoreDTO.getType().toUpperCase());
        if (isNewScore(pilotScores, newScore)) {
            List<PScore> currentScores = pilotScores.getScores();
            currentScores.add(newScore);
            pilotScores.setScores(currentScores);
            // update next seq / round
            pilotScores = setNextEvent(pilotScores,pilotScoreDTO.getType().toUpperCase());
            pilotScores.setActiveRoundType(pilotScoreDTO.getType().toUpperCase());
            // save to file
            pilotScores = savePilotScoresToFile(pilotScores);
        } else {
            // BACKLOG 2025-11 DPG: update existing score
        }
        return pilotScores;
    }

    public boolean isNewScore(PilotScores pilotScores, PScore score) {
        for (PScore savedScore : pilotScores.getScores()) {
            // Must match round, sequence, AND type (so KNOWN R1 and FREESTYLE R1 are different)
            if (savedScore.getRound() == score.getRound()
                    && savedScore.getSequence() == score.getSequence()
                    && savedScore.getType() != null
                    && savedScore.getType().equalsIgnoreCase(score.getType())) {
                return false;
            }
        }
        return true;
    }

    public PilotScores setNextEvent(PilotScores pilotScore , String roundType) throws IOException {
        String type = roundType.toUpperCase();
        int compSequences = sequencesForType(type);
        int currentSequence = pilotScore.getActiveSequence(type);

        if (currentSequence < compSequences) {
            pilotScore.setActiveSequence(type, currentSequence + 1);
        } else if (currentSequence == compSequences) {
            // Each type (KNOWN, UNKNOWN, FREESTYLE) has its own independent round counter.
            pilotScore.incrementActiveRound(type);
            pilotScore.setActiveSequence(type, 1);
        } else {
            logger.warn("activeSequence {} > compSequences {} for type {}; resetting and advancing round",
                    currentSequence, compSequences, type);
            pilotScore.incrementActiveRound(type);
            pilotScore.setActiveSequence(type, 1);
        }
        return pilotScore;
    }

    private int sequencesForType(String type) throws IOException {
        CompDTO compDTO = compService.getComp();
        if(type.equalsIgnoreCase("KNOWN")){
            return compDTO.getSequences();
        }
        if(type.equalsIgnoreCase("UNKNOWN")){
            return compDTO.getUnknown_sequences();
        }
        if(type.equalsIgnoreCase("FREESTYLE")){
            return 1;  // Freestyle always has 1 sequence per round
        }
        logger.warn("Unknown round type '{}' - defaulting to 1 sequence", type);
        return 1;
    }

    public void syncPilotsToScoreWebService() throws IOException, ParserConfigurationException, SAXException, UnirestException {
        SettingDTO settingDTO = settingService.getSettings();
        List<FlightUploadDTO> flight = new ArrayList<>();
        int index = 0;
        List<PilotScores> pilotScores = new ArrayList<>();
        List<Pilot> pilots = getPilots();
        for (Pilot pilot : pilots) {
            pilotScores.add(getPilotScores(pilot));
        }
        for (PilotScores pilotScore : pilotScores) {
            if (pilotScore.getScores().size() > 0) {
                for (PScore score : pilotScore.getScores()) {
                    List<FigureUploadDTO> figureScores = new ArrayList<>();
                    for (int i = 0; i < score.getScores().length; i++) {
                        float fscore = score.getScores()[i];
                        boolean break_err = false;
                        boolean box_err = false;
                        boolean not_observed = false;
                        if (fscore == -1) {
                            fscore = 0;
                            break_err = true;
                        }
                        if (fscore == -2) {
                            fscore = 0;
                            not_observed = true;
                        }
                        if (fscore == -3) {
                            fscore = 0;
                            box_err = true;
                        }
                        FigureUploadDTO figureScore = new FigureUploadDTO(fscore, box_err, break_err,not_observed, i);
                        figureScores.add(figureScore);
                    }
                    FiguresUploadDTO figures = new FiguresUploadDTO(figureScores);
                    // create FlightUploadDTO
                    FlightUploadDTO flightUploadDTO = new FlightUploadDTO(pilotScore.getPrimary_id(), score.getType(), 
                            score.getRound(),
                            score.getSequence(), pilotScore.getJudge_id(), false, figures, index, settingDTO.getLine_number());
                    flight.add(flightUploadDTO);
                    index++;
                }
            }
            // create final stupid wrapper
        }

        FlightsUploadDTO flightsUploadDTO = new FlightsUploadDTO(flight, settingDTO.getLine_number());

        XmlMapper xmlMapper = new XmlMapper();
        String xml = xmlMapper.writeValueAsString(flightsUploadDTO);

        //SettingDTO settingDTO = settingService.getSettings();
        SCORE_UPLOAD_URL = SCORE_UPLOAD_URL.replace("SCORE_HOST", settingDTO.getScore_host()).replace("SCORE_HTTP_PORT", String.valueOf(settingDTO.getScore_http_port()));

        String flights_dat_file_name = "LINE" + settingDTO.getLine_number() + "_JUDGE" + settingDTO.getJudge_id() + "_flights.dat";
        String flights_dat_file_path = PILOT_SCORE_DIR + flights_dat_file_name;
        File flights_dat_file = new File(flights_dat_file_path);
        FileWriter fw = new FileWriter(flights_dat_file.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(xml);
        bw.close();
        Unirest.setTimeouts(Duration.ofSeconds(settingDTO.getScore_timeout()).toMillis(), Duration.ofSeconds(settingDTO.getScore_timeout()).toMillis());
        HttpResponse<String> response = Unirest.post(SCORE_UPLOAD_URL)
                .header("Accept-Language", "en-au")
                .field("uploadtype", "flightdata")
                .field("keepbackup", "false")
                .field("submit", "submit")
                .field("file", flights_dat_file)
                .field("filename", flights_dat_file_name)
                .asString();
        if(response.getStatus() != 200){
            throw new IOException("Unable to sync to Score");
        }
        System.out.println(response.getStatus());
        System.out.println(xml);
    }
}
