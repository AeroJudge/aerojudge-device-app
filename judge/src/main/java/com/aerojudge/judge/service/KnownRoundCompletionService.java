package com.aerojudge.judge.service;

import com.aerojudge.judge.dto.CompDTO;
import com.aerojudge.judge.dto.PScore;
import com.aerojudge.judge.dto.Pilot;
import com.aerojudge.judge.dto.PilotScores;
import com.aerojudge.judge.dto.RoundDTO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Explicit Home confirmation only; score saving never calls this service. */
@Service
public class KnownRoundCompletionService {
    private static final Logger logger = LoggerFactory.getLogger(KnownRoundCompletionService.class);
    private final CompService compService;
    private final PilotService pilotService;
    private final RoundsService roundsService;

    public KnownRoundCompletionService(CompService compService, PilotService pilotService,
            RoundsService roundsService) {
        this.compService = compService;
        this.pilotService = pilotService;
        this.roundsService = roundsService;
    }

    public synchronized Map<String, Object> previewKnownRoundCompletion() {
        try {
            return inspect().response();
        } catch (Exception e) {
            logger.error("Could not preview Known round completion", e);
            return failure("read", "Could not read local round or pilot data. Return to judging and have the data checked.", false);
        }
    }

    // Serializes this operation, not other application writers. Revalidate before every apply.
    public synchronized Map<String, Object> applyKnownRoundCompletion(int expectedRoundId) {
        Assessment assessment;
        try {
            RoundDTO current = roundsService.getScoringRound();
            if (current == null || !Integer.valueOf(expectedRoundId).equals(current.getRound_id())) {
                return failure("validation", "The displayed round is no longer active. Return to judging.", false);
            }
            assessment = inspect();
            if (!Boolean.TRUE.equals(assessment.response().get("viable"))) {
                return failure("validation", (String) assessment.response().get("message"), false);
            }
        } catch (Exception e) {
            logger.error("Could not validate Known round completion", e);
            return failure("read", "Could not read local round or pilot data. No completion writes were started.", false);
        }

        int advanced = 0;
        try {
            for (PilotScores scores : assessment.scores()) {
                scores.setActiveRound("KNOWN", assessment.round().getRound_num() + 1);
                scores.setActiveSequence("KNOWN", 1);
                pilotService.savePilotScoresToFile(scores);
                advanced++;
            }
            Map<String, Object> closed = roundsService.closeRound(expectedRoundId);
            if (!Boolean.TRUE.equals(closed.get("success"))) {
                throw new IOException(String.valueOf(closed.get("message")));
            }
        } catch (Exception e) {
            // Even the first attempted save can leave an incomplete file. Never offer blind retry.
            logger.error("Known round {} completion failed after {} pilot saves; diagnosis required",
                    expectedRoundId, advanced, e);
            return failure("storage", "Round completion could not be saved. Some files may have changed. "
                    + "Do not retry; give this device to the Contest Director or Scorekeeper for recovery.", true);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("result", "ok");
        result.put("roundClosed", true);
        result.put("roundId", expectedRoundId);
        result.put("roundLabel", assessment.response().get("roundLabel"));
        result.put("pilotsAdvancedCount", advanced);
        logger.info("Completed Known round {} as single-sequence for {} pilots", expectedRoundId, advanced);
        return result;
    }

    private Assessment inspect() throws Exception {
        CompDTO comp = compService.getComp();
        if (comp == null) throw new IOException("Missing competition data.");
        if (!"byRound".equalsIgnoreCase(comp.getScore_mode()) || comp.getSequences() != 2) {
            return ineligible("Single-sequence completion is not available for these competition settings.");
        }
        RoundDTO round = roundsService.getScoringRound();
        if (round == null || !"KNOWN".equalsIgnoreCase(round.getType())) {
            return ineligible("No active Known round is available.");
        }
        if (round.getRound_id() == null || round.getRound_id() < 0 || round.getRound_num() == null
                || round.getRound_num() < 1 || round.getRound_num() == Integer.MAX_VALUE
                || round.getComp_class() == null || round.getComp_class().isBlank()) {
            throw new IOException("Invalid active round data.");
        }
        List<Pilot> cohort = pilotService.getLocalActivePilots().stream()
                .filter(p -> round.getComp_class().equalsIgnoreCase(p.getClassString())).toList();
        if (cohort.isEmpty()) return ineligible("No active pilots belong to this round.");

        List<PilotScores> allScores = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        boolean eligible = true;
        for (Pilot pilot : cohort) {
            ids.add(pilot.getPrimary_id());
            PilotScores scores = pilotService.getLocalPilotScores(pilot);
            int seq1 = 0;
            boolean seq2 = false;
            for (PScore score : scores.getScores()) {
                if ("KNOWN".equalsIgnoreCase(score.getType()) && score.getRound() == round.getRound_num()) {
                    if (score.getSequence() == 1) seq1++;
                    if (score.getSequence() == 2) seq2 = true;
                }
            }
            eligible &= seq1 == 1 && !seq2 && scores.getActiveRound("KNOWN") == round.getRound_num()
                    && scores.getActiveSequence("KNOWN") == 2;
            allScores.add(scores);
        }
        String className = round.getComp_class();
        String label = className.substring(0, 1).toUpperCase() + className.substring(1).toLowerCase()
                + " Known Round " + round.getRound_num();
        Map<String, Object> response = new HashMap<>();
        response.put("result", "ok");
        response.put("viable", eligible);
        response.put("message", eligible ? "This Known round can finish with one sequence."
                : "Every active pilot must have only sequence 1 saved and be ready for sequence 2.");
        response.put("roundId", round.getRound_id());
        response.put("roundNum", round.getRound_num());
        response.put("roundLabel", label);
        response.put("className", className);
        response.put("activePilotCount", cohort.size());
        response.put("pilotsToAdvance", ids);
        return new Assessment(response, round, allScores);
    }

    private Assessment ineligible(String message) {
        return new Assessment(Map.of("result", "ok", "viable", false, "message", message), null, List.of());
    }

    private Map<String, Object> failure(String kind, String message, boolean recoveryRequired) {
        return Map.of("result", "fail", "failureKind", kind, "message", message,
                "recoveryRequired", recoveryRequired);
    }

    private record Assessment(Map<String, Object> response, RoundDTO round, List<PilotScores> scores) {}
}
