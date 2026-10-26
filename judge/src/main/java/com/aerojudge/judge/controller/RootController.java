package com.aerojudge.judge.controller;

import java.io.IOException;
import java.util.ArrayList;
import com.aerojudge.judge.utils.ContestClasses;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import com.aerojudge.judge.dto.RoundDTO;
import com.aerojudge.judge.dto.SettingDTO;
import com.aerojudge.judge.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.xml.sax.SAXException;

import com.google.gson.Gson;
import com.mashape.unirest.http.exceptions.UnirestException;

import com.aerojudge.judge.dto.CompDTO;
import com.aerojudge.judge.dto.FigureDTO;
import com.aerojudge.judge.dto.PScore;
import com.aerojudge.judge.dto.ScheduleDTO;
import com.aerojudge.judge.dto.Pilot;
import com.aerojudge.judge.dto.PilotScores;

@Controller
public class RootController {
    private static final Logger logger = LoggerFactory.getLogger(RootController.class);

    @Autowired
    private CompService compService;
    @Autowired
    private RoundsService roundService;
    @Autowired
    private PilotService pilotService;
    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private SettingService settingService;
    @Autowired
    private SequenceFolderResolver sequenceFolderResolver;

    @GetMapping("/")
    public String home(Model model)
            throws IOException, ParserConfigurationException, SAXException, UnirestException {

        // Check if we have seen the splash-screen.
        // unnecessary 
        // if (settingService.isFirstRun()) {
        //     logger.info("This is our first run.");
        //     settingService.setFirstRun(false);
        //     return "index";
        // }


        /**********
         * Ok. Lets check out the stuff we *need* to proceed.
         *
         * settings: settings.json via settingsService.getSettings().
         * We dont need to check this because we create an empty
         * one if it does not exist.
         *
         * comp: If there's no file, then we ask to create one.
         * Score wont tell us how many unknown rounds to score
         * so we need to have it configured.
         *
         * pilots: Fail if we cant get it or it does not exist.
         *
         * sequences: Fail if we cant get it or it does not exist.
         *
         * contest_prefs: We only need it for name (now and then it's not that
         * important).
         */

        
        if (!compService.isCurrentComp()) {
            logger.debug("Redirect to newcomp page.");
            logger.info("There is no Comp!! redirecting");
            return "redirect:/newcomp";
        }
        return redirectForCurrentScoringMode();

        // // Now if we have a comp, are we scoring a round?
        // logger.info("Are we scoring a round?? : " + roundService.isScoringRound());
        // if (!roundService.isScoringRound()) {
        //     logger.debug("Redirect to new round page.");
        //     return "redirect:/rounds";
        // }

        // if ("global".equalsIgnoreCase(compService.getComp().getScore_mode()))
        //     return "redirect:/pilot-list-global";
        // else
        //     return "redirect:/rounds";
    }

    @GetMapping("/pilot-list-global")
    public String pilotListGlobal(Model model,@RequestParam(name = "classFilter", required = false) String classFilter)
            throws IOException, ParserConfigurationException, SAXException, UnirestException {

        // Check first if we have a valid comp
        //logger.info("Is there a comp? : " + compService.isCurrentComp());
        if (!compService.isCurrentComp()) {
            logger.debug("Redirect to newcomp page.");
            logger.info("There is no Comp!! redirecting");
            return "redirect:/newcomp";
        }
        if (isByRoundMode()) {
            if (roundService.isScoringRound()) {
                return "redirect:/pilot-list-round";
            }
            return "redirect:/newround";
        }

        List<Pilot> pilots = pilotService.getPilots(true);

        //now get ordered list of classes for the filter dialog
        Set<String> pilot_classes = new LinkedHashSet<>();
        //build ordered list of classes from those contained in the pilots list
        for (String className : ContestClasses.ORDER) {
            if (pilots.stream().anyMatch(pilot -> className.equalsIgnoreCase(pilot.getClassString()))) {
                pilot_classes.add(className);
            }
        }
        // Add FREESTYLE option if any pilot has freestyle=true
        if (pilots.stream().anyMatch(p -> Boolean.TRUE.equals(p.getFreestyle()))) {
            pilot_classes.add("FREESTYLE");
        }
        model.addAttribute("pilotClasses", pilot_classes);


        if(classFilter != null && !classFilter.isEmpty() && !classFilter.equals("global")){
            if (classFilter.equalsIgnoreCase("FREESTYLE")) {
                pilots = pilots.stream().filter(pilot -> Boolean.TRUE.equals(pilot.getFreestyle())).toList();
            } else {
                pilots = pilots.stream().filter(pilot -> pilot.getClassString().equalsIgnoreCase(classFilter)).toList();
            }
        }

        // Get settings so we can show them
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);
        
        model.addAttribute("pilots", pilots);
        HashMap<String, PilotScores> pilotScores = new HashMap<>();

        for (Pilot pilot : pilots) {
            pilotScores.put(pilot.getPrimary_id(), pilotService.getPilotScores(pilot));
        }
        model.addAttribute("pilotScores", pilotScores);

        model.addAttribute("comp", compService.getComp());
        model.addAttribute("dirflip", false);

        // Sequence availability for modal buttons
        model.addAttribute("classesWithUnknown", scheduleService.getClassesWithUnknown());
        model.addAttribute("hasFreestyle", scheduleService.hasFreestyle());

        return "pilot-list-global";
    }

    @GetMapping("/pilot-list-round")
    public String pilotListRound(Model model,@RequestParam(name = "classFilter", required = false) String classFilter,
            RedirectAttributes redirectAttributes)
            throws IOException, ParserConfigurationException, SAXException, UnirestException {
        /******************
         * Create the carousel for the pilot selector.
         * Before we send the list to the frontend, filter it first.
         *
         * We can only do that if we have a clear active round.
         */
        // Check first if we have a valid comp
        logger.info("Is there a comp? : " + compService.isCurrentComp());
        if (!compService.isCurrentComp()) {
            logger.debug("Redirect to newcomp page.");
            return "redirect:/newcomp";
        }

        // Now if we have a comp, are we scoring a round?
        RoundDTO roundToScore = roundService.getScoringRound();
        logger.info("Are we scoring a round?? : " + (roundToScore != null));
        if (roundToScore == null) {
            // Tell them to choose a round to Score.
            logger.debug("Redirect to new round page.");
            if (isByRoundMode()) {
                return "redirect:/newround";
            }
            return "redirect:/rounds";
        }

        List<Pilot> pilots = pilotService.getPilots(true);
        List<Pilot> activeCohort = pilots.stream()
                .filter(pilot -> isPilotInActiveRoundCohort(pilot, roundToScore))
                .toList();

        List<Pilot> filteredPilots = new ArrayList<>();
        List<String> behindPilotNames = new ArrayList<>();
        HashMap<String, PilotScores> pilotScores = new HashMap<>();
        for (Pilot pilot : activeCohort) {
            PilotScores scores = pilotService.getPilotScores(pilot);
            int pilotActiveRound = scores.getActiveRound(roundToScore.getType());
            if (pilotActiveRound < roundToScore.getRound_num()) {
                behindPilotNames.add(pilot.getName() + " (Round " + pilotActiveRound + ")");
            }
            if (pilotActiveRound == roundToScore.getRound_num()) {
                filteredPilots.add(pilot);
                pilotScores.put(pilot.getPrimary_id(), scores);
            }
        }

        if (!behindPilotNames.isEmpty()) {
            model.addAttribute("errorTitle", "RBR Round Alignment Error");
            model.addAttribute("errorMessage", "These pilots are behind "
                    + getRoundContextLabel(roundToScore)
                    + ": " + String.join(", ", behindPilotNames)
                    + ". Give this device to the Contest Director or Scorekeeper to make the needed score edits.");
            return "error";
        }

        if (filteredPilots.isEmpty()) {
            String completedRoundLabel = getRoundContextLabel(roundToScore);
            int completedPilotCount = activeCohort.size();
            Map<String, Object> closeResult = roundService.closeRound(roundToScore.getRound_id());
            if ((Boolean) closeResult.get("success")) {
                redirectAttributes.addFlashAttribute("completedRoundLabel", completedRoundLabel);
                redirectAttributes.addFlashAttribute("completedPilotCount", completedPilotCount);
                return "redirect:/round-complete";
            }
            model.addAttribute("errorTitle", "RBR Round Close Error");
            model.addAttribute("errorMessage", closeResult.get("message"));
            return "error";
        }

        //now get ordered list of classes for the filter dialog
        Set<String> pilot_classes = new LinkedHashSet<>();
        //build ordered list of classes from those contained in the pilots list
        for (String className : ContestClasses.ORDER) {
            if (pilots.stream().anyMatch(pilot -> className.equalsIgnoreCase(pilot.getClassString()))) {
                pilot_classes.add(className);
            }
        }
        // Add FREESTYLE option if any pilot has freestyle=true
        if (pilots.stream().anyMatch(p -> Boolean.TRUE.equals(p.getFreestyle()))) {
            pilot_classes.add("FREESTYLE");
        }
        model.addAttribute("pilotClasses", pilot_classes);

        model.addAttribute("pilots", filteredPilots);
        model.addAttribute("pilotScores", pilotScores);
        model.addAttribute("comp", compService.getComp());
        model.addAttribute("activeRound", roundToScore);
        model.addAttribute("activeRoundType", roundToScore.getType());
        model.addAttribute("activeRoundNum", roundToScore.getRound_num());
        model.addAttribute("activeRoundLabel", getRoundContextLabel(roundToScore));

        // Get settings so we can show them
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);

        // Sequence availability for modal buttons
        model.addAttribute("classesWithUnknown", scheduleService.getClassesWithUnknown());
        model.addAttribute("hasFreestyle", scheduleService.hasFreestyle());

        return "pilot-list-round";
    }

    @GetMapping("/judge")
    public String judge(@RequestParam(name = "pilot_id", required = true) String pilot_id,
            @RequestParam(name = "roundType", required = true) String roundType,
            @RequestParam(name = "dirflip", required = false, defaultValue = "false") Boolean dirflip, 
            @RequestParam(name = "sequenceType", required = false) String sequenceType, Model model)
            throws IOException, ParserConfigurationException, SAXException {

        if (!compService.isCurrentComp()) {
            logger.debug("Redirect to newcomp page.");
            return "redirect:/newcomp";
        }
        
        // Get sequenceType from CompDTO if not provided (legacy fallback for blank short_desc)
        if (sequenceType == null || sequenceType.isEmpty()) {
            sequenceType = compService.getComp().getSequenceType();
            if (sequenceType == null) sequenceType = "std";
        }
        
        Pilot pilot = pilotService.getPilot(pilot_id);
        logger.debug("Pilot data:");
        logger.debug(new Gson().toJson(pilot));
        PilotScores pilotScores = pilotService.getPilotScores(pilot);

        // DEBUG: Log the round number being used
        // Use per-type round number (KNOWN, UNKNOWN, FREESTYLE each have independent counters)
        int activeRoundForType = pilotScores.getActiveRound(roundType);
        int activeSequenceForType = pilotScores.getActiveSequence(roundType);
        logger.info("=== JUDGE PAGE DEBUG ===");
        logger.info("Pilot: {} ({})", pilot.getName(), pilot.getClassString());
        logger.info("Round Type: {}", roundType);
        logger.info("Active Round for {}: {}", roundType, activeRoundForType);
        logger.info("Active Sequence for {}: {}", roundType, activeSequenceForType);
        logger.info("All rounds by type: {}", pilotScores.getActiveRoundByType());

        // Resolve the folder path for figures using the new folder structure
        String sequenceFolderPath = sequenceFolderResolver.resolve(
                pilot.getClassString(),
                roundType,
                activeRoundForType,
                sequenceType
        );
        logger.info("Resolved folder path: {}", sequenceFolderPath);

        // Get round-aware schedule with figures
        ScheduleDTO schedule = scheduleService.getScheduleForRound(
                pilot.getClassString(),
                roundType,
                activeRoundForType
        );
        if (schedule == null || schedule.getFigures() == null || schedule.getFigures().isEmpty()) {
            return "noseq";
        }
        
        // Convert figures map to list (sorted by figure number)
        List<FigureDTO> sequences = new ArrayList<>(schedule.getFigures().values());
        sequences.sort((a, b) -> Integer.compare(a.getFigNum(), b.getFigNum()));
        
        // Get settings for language
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);

        model.addAttribute("maneuvers", sequences);
        model.addAttribute("numOfManeuvers", sequences.size());
        model.addAttribute("pilot", pilot);
        model.addAttribute("pilotScores", pilotScores);
        model.addAttribute("activeRound", activeRoundForType);
        model.addAttribute("activeSequence", activeSequenceForType);
        model.addAttribute("roundType", roundType.toUpperCase());
        model.addAttribute("scoreMode", compService.getComp().getScore_mode());
        RoundDTO judgingRound = roundService.getScoringRound();
        model.addAttribute("judgingRoundId", judgingRound == null ? null : judgingRound.getRound_id());
        model.addAttribute("sequenceType", sequenceType);
        model.addAttribute("sequenceFolderPath", sequenceFolderPath);
        model.addAttribute("pilot_class", pilot.getClassString());
        String sequencesJson = new Gson().toJson(sequences);
        model.addAttribute("sequencesjson", sequencesJson);
        model.addAttribute("dirletter", (dirflip == true ? "C" : "B"));
        logger.debug("Sequence data:");
        logger.debug(new Gson().toJson(sequences));
        
        return "judge";
    }

    @GetMapping("/newcomp")
    public String newcomp(Model model) throws IOException {
        // Get settings so we can show them
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);
        model.addAttribute("lineNumber", settings.getLine_number());
        model.addAttribute("judgeId", settings.getJudge_id());

        boolean isComp = compService.isCurrentComp();
        logger.info("Is there a comp? : " + isComp);
        model.addAttribute("isCurrentComp", isComp);
        if (isComp) {
            CompDTO curComp = compService.getComp();
            model.addAttribute("compName", curComp.getComp_name());
            model.addAttribute("compId", curComp.getComp_id());
            model.addAttribute("scoreMode", curComp.getScore_mode());
            model.addAttribute("maxSeqPerRound", curComp.getSequences());
            model.addAttribute("maxUnknownSeqPerRound", curComp.getUnknown_sequences());
            model.addAttribute("maxUnknownSeqPerRound", curComp.getUnknown_sequences());
            model.addAttribute("sequenceType", curComp.getSequenceType());
        } else {
            model.addAttribute("compName", "Untitled Comp");
            model.addAttribute("compId", 0);
            model.addAttribute("scoreMode", "global");
            model.addAttribute("maxSeqPerRound", 2);
            model.addAttribute("maxUnknownSeqPerRound", 1);
            model.addAttribute("sequenceType", "std");
        }
        return "newcomp";
    }

    @GetMapping("/rounds")
    public String showRounds(@RequestParam(defaultValue = "completed", required = false) String mode, Model model)
            throws IOException {
        return redirectForCurrentScoringMode();
    }

    @GetMapping("/newround")
    public String newRound(Model model) throws IOException {
        if (!compService.isCurrentComp()) {
            logger.debug("Redirect to newcomp page.");
            return "redirect:/newcomp";
        }

        if (roundService.isScoringRound()) {
            logger.info("Active round exists. Redirecting from newround to pilot-list-round.");
            return "redirect:/pilot-list-round";
        }

        // We need to send the list of rounds and available schedules to the page.
        model.addAttribute("settings", settingService.getSettings());
        model.addAttribute("rounds", roundService.getRounds());
        model.addAttribute("schedules", scheduleService.getSchedules());
        model.addAttribute("roundOptions", buildRoundOptions());
        logger.debug("Schedule Count: " + scheduleService.getSchedules().size());
        return "newround";
    }

    @GetMapping("/round-complete")
    public String roundComplete(Model model) throws IOException {
        if (!compService.isCurrentComp()) {
            return "redirect:/newcomp";
        }
        if (roundService.isScoringRound()) {
            return "redirect:/pilot-list-round";
        }

        if (!model.containsAttribute("completedRoundLabel")) {
            model.addAttribute("completedRoundLabel", "Round Complete");
        }
        if (!model.containsAttribute("completedPilotCount")) {
            model.addAttribute("completedPilotCount", null);
        }
        if (!model.containsAttribute("singleSequenceCompleted")) {
            model.addAttribute("singleSequenceCompleted", false);
        }
        model.addAttribute("settings", settingService.getSettings());
        model.addAttribute("attemptAutoSyncScores",
                "byRound".equalsIgnoreCase(compService.getComp().getScore_mode())
                && Boolean.TRUE.equals(compService.getComp().getAttempt_auto_sync_scores()));
        return "round-complete";
    }

    @GetMapping("/admin/comp")
    public String adminComp(Model model) throws IOException {
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);

        boolean isComp = compService.isCurrentComp();
        model.addAttribute("isCurrentComp", isComp);
        if (isComp) {
            CompDTO curComp = compService.getComp();
            model.addAttribute("compName", curComp.getComp_name());
            model.addAttribute("compId", curComp.getComp_id());
            model.addAttribute("scoreMode", curComp.getScore_mode());
            model.addAttribute("maxSeqPerRound", curComp.getSequences());
            model.addAttribute("maxUnknownSeqPerRound", curComp.getUnknown_sequences());
            model.addAttribute("sequenceType", curComp.getSequenceType());
        } else {
            model.addAttribute("compName", "No Competition");
            model.addAttribute("compId", 0);
            model.addAttribute("scoreMode", "byRound");
            model.addAttribute("maxSeqPerRound", 2);
            model.addAttribute("maxUnknownSeqPerRound", 1);
            model.addAttribute("sequenceType", "std");
        }
        return "adminComp";
    }

    @GetMapping("/admin/device")
    public String adminDevice(Model model) throws IOException {
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);
        model.addAttribute("lineNumber", settings.getLine_number());
        model.addAttribute("judgeId", settings.getJudge_id());

        if (compService.isCurrentComp()) {
            model.addAttribute("scoreMode", compService.getComp().getScore_mode());
            model.addAttribute("attemptAutoSyncScores",
                    Boolean.TRUE.equals(compService.getComp().getAttempt_auto_sync_scores()));
        } else {
            model.addAttribute("scoreMode", "global");
            model.addAttribute("attemptAutoSyncScores", false);
        }
        return "adminDevice";
    }

    @GetMapping("/admin")
    public String admin(Model model) throws IOException, ParserConfigurationException, SAXException {
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);
        model.addAttribute("compName", compService.getComp().getComp_name());
        RoundDTO activeRound = roundService.getScoringRound();
        model.addAttribute("activeRoundLabel", activeRound == null ? null : getRoundContextLabel(activeRound));
        model.addAttribute("activeRoundCanClear", activeRound != null && !activeRoundHasScores(activeRound));
        return "admin";
    }

    @GetMapping("/admin/scores")
    public String adminScores(Model model) throws IOException {
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);
        return "adminScores";
    }

    @GetMapping("/admin/scores/resolve")
    public String adminScoresResolve(Model model) throws IOException {
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);
        return "adminScoresResolve";
    }

    @GetMapping("/admin/scores/zero-fill")
    public String adminScoresZeroFill(Model model) throws IOException {
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);
        return "adminScoresZeroFill";
    }

    @GetMapping("/admin/scores/swap")
    public String adminScoresSwap(Model model) throws IOException {
        SettingDTO settings = settingService.getSettings();
        model.addAttribute("settings", settings);
        return "adminScoresSwap";
    }

    private boolean isByRoundMode() {
        return compService.getComp() != null && "byRound".equalsIgnoreCase(compService.getComp().getScore_mode());
    }

    private boolean isPilotInActiveRoundCohort(Pilot pilot, RoundDTO round) {
        if ("FREESTYLE".equalsIgnoreCase(round.getType())) {
            return Boolean.TRUE.equals(pilot.getFreestyle());
        }
        return pilot.getClassString() != null && pilot.getClassString().equalsIgnoreCase(round.getComp_class());
    }

    private boolean activeRoundHasScores(RoundDTO round)
            throws ParserConfigurationException, SAXException, IOException {
        for (Pilot pilot : pilotService.getPilots(false)) {
            if (!isPilotInActiveRoundCohort(pilot, round)) {
                continue;
            }
            PilotScores pilotScores = pilotService.getPilotScores(pilot);
            for (PScore score : pilotScores.getScores()) {
                if (round.getType().equalsIgnoreCase(score.getType()) && score.getRound() == round.getRound_num()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getRoundContextLabel(RoundDTO round) {
        String type = displayType(round.getType());
        if ("FREESTYLE".equalsIgnoreCase(round.getType())) {
            return type + " Round " + round.getRound_num();
        }
        return displayClass(round.getComp_class()) + " " + type + " Round " + round.getRound_num();
    }

    private String displayType(String type) {
        if ("KNOWN".equalsIgnoreCase(type)) {
            return "Known";
        }
        if ("UNKNOWN".equalsIgnoreCase(type)) {
            return "Unknown";
        }
        if ("FREESTYLE".equalsIgnoreCase(type)) {
            return "Freestyle";
        }
        return type;
    }

    private String displayClass(String compClass) {
        if (compClass == null || compClass.isBlank()) {
            return "";
        }
        return compClass.substring(0, 1).toUpperCase() + compClass.substring(1).toLowerCase();
    }

    private String redirectForCurrentScoringMode() throws IOException {
        if (compService.getComp() == null) {
            return "redirect:/newcomp";
        }
        String scoreMode = compService.getComp().getScore_mode();
        if ("byRound".equalsIgnoreCase(scoreMode)) {
            if (roundService.isScoringRound()) {
                return "redirect:/pilot-list-round";
            }
            return "redirect:/newround";
        }
        return "redirect:/pilot-list-global";
    }

    private List<Map<String, Object>> buildRoundOptions() throws IOException {
        List<Map<String, Object>> roundOptions = new ArrayList<>();
        List<Pilot> activePilots;
        try {
            activePilots = pilotService.getPilots(true);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Could not load pilots for round selection.", e);
        }

        Set<String> activeClasses = new LinkedHashSet<>();
        for (String className : ContestClasses.ORDER) {
            if (activePilots.stream().anyMatch(pilot -> className.equalsIgnoreCase(pilot.getClassString()))) {
                activeClasses.add(className);
            }
        }

        for (String className : activeClasses) {
            addPrecisionRoundOption(roundOptions, activePilots, className, "KNOWN");
            addPrecisionRoundOption(roundOptions, activePilots, className, "UNKNOWN");
        }

        if (activePilots.stream().anyMatch(pilot -> Boolean.TRUE.equals(pilot.getFreestyle()))) {
            addFreestyleRoundOption(roundOptions, activePilots);
        }

        return roundOptions;
    }

    private void addPrecisionRoundOption(List<Map<String, Object>> roundOptions, List<Pilot> activePilots,
            String className, String type) throws IOException {
        List<Pilot> cohort = activePilots.stream()
                .filter(pilot -> className.equalsIgnoreCase(pilot.getClassString()))
                .toList();
        if (cohort.isEmpty()) {
            return;
        }

        Integer roundNum = getAlignedRoundNum(cohort, type);
        ScheduleDTO schedule = roundNum == null ? null : scheduleService.getScheduleForRound(className, type, roundNum);
        if (schedule == null) {
            return;
        }

        roundOptions.add(createRoundOption("Precision", className, type, roundNum, schedule, cohort.size()));
    }

    private void addFreestyleRoundOption(List<Map<String, Object>> roundOptions, List<Pilot> activePilots)
            throws IOException {
        List<Pilot> cohort = activePilots.stream()
                .filter(pilot -> Boolean.TRUE.equals(pilot.getFreestyle()))
                .toList();
        if (cohort.isEmpty()) {
            return;
        }

        Integer roundNum = getAlignedRoundNum(cohort, "FREESTYLE");
        ScheduleDTO schedule = roundNum == null ? null : scheduleService.getScheduleForRound("FREESTYLE", "FREESTYLE", roundNum);
        if (schedule == null) {
            return;
        }

        roundOptions.add(createRoundOption("Freestyle", null, "FREESTYLE", roundNum, schedule, cohort.size()));
    }

    private Integer getAlignedRoundNum(List<Pilot> cohort, String type) throws IOException {
        Integer roundNum = null;
        for (Pilot pilot : cohort) {
            int pilotRound = pilotService.getPilotScores(pilot).getActiveRound(type);
            if (roundNum == null) {
                roundNum = pilotRound;
            } else if (roundNum != pilotRound) {
                logger.warn("RBR {} cohort is not aligned for {}. Expected round {}, found {} for {}.",
                        type, pilot.getClassString(), roundNum, pilotRound, pilot.getName());
                return null;
            }
        }
        return roundNum;
    }

    private Map<String, Object> createRoundOption(String family, String compClass, String type, Integer roundNum,
            ScheduleDTO schedule, int pilotCount) {
        Map<String, Object> option = new HashMap<>();
        option.put("family", family);
        option.put("compClass", compClass);
        option.put("type", type);
        option.put("roundNum", roundNum);
        option.put("schedId", findScheduleId(schedule));
        option.put("schedDesc", getScheduleDescription(schedule, compClass, type));
        option.put("sequences", getSequenceCount(type));
        option.put("sequenceCount", schedule.getFigures() == null ? 0 : schedule.getFigures().size());
        option.put("pilotCount", pilotCount);
        return option;
    }

    private Integer findScheduleId(ScheduleDTO targetSchedule) {
        for (Map.Entry<Integer, ScheduleDTO> entry : scheduleService.getSchedules().entrySet()) {
            if (entry.getValue() == targetSchedule) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String getScheduleDescription(ScheduleDTO schedule, String compClass, String type) {
        if (schedule.getDescription() != null && !schedule.getDescription().isBlank()) {
            return schedule.getDescription();
        }
        if (schedule.getShort_desc() != null && !schedule.getShort_desc().isBlank()) {
            return schedule.getShort_desc();
        }
        if (compClass == null) {
            return type;
        }
        return compClass + " " + type;
    }

    private int getSequenceCount(String type) {
        CompDTO comp = compService.getComp();
        if ("KNOWN".equalsIgnoreCase(type)) {
            return comp.getSequences();
        }
        if ("UNKNOWN".equalsIgnoreCase(type)) {
            return comp.getUnknown_sequences();
        }
        return 1;
    }
}
