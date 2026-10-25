package com.aerojudge.judge.service;

import com.aerojudge.judge.controller.APIController;
import com.aerojudge.judge.controller.RootController;
import com.aerojudge.judge.dto.*;
import com.aerojudge.judge.utils.SettingUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnownRoundCompletionTest {
    @TempDir static Path data;
    private CompService comp;
    private CountingPilots pilots;
    private RoundsService rounds;
    private KnownRoundCompletionService service;
    private final Gson gson = new Gson();

    @BeforeAll static void isolateData() throws IOException {
        SettingUtils.setApplicationConfigPath(data.toString());
    }

    @BeforeEach void setup() throws Exception {
        Files.createDirectories(data.resolve("pilots/scores"));
        Files.writeString(data.resolve("rounds.json"), "{\"comp_id\":1,\"scoringRoundNum\":null,\"rounds\":[]}");
        comp = new CompService();
        ReflectionTestUtils.setField(comp, "compDTO", new CompDTO("byRound", 2, 1));
        pilots = new CountingPilots();
        ReflectionTestUtils.setField(pilots, "compService", comp);
        rounds = new RoundsService();
        RoundDTO round = new RoundDTO(7);
        round.setType("KNOWN"); round.setComp_class("SPORTSMAN"); round.setRound_num(3);
        rounds.addRound(round); rounds.activateRoundForScoring(7);
        service = new KnownRoundCompletionService(comp, pilots, rounds);
        fixture(2);
    }

    private void fixture(int count) throws Exception {
        StringBuilder xml = new StringBuilder("<score><pilots>");
        for (int i = 0; i < count; i++) {
            xml.append(rosterPilot(i, "p" + i, "SPORTSMAN", true, false));
            writeScores("p" + i, ready("p" + i));
        }
        xml.append("</pilots></score>");
        Files.writeString(data.resolve("pilots.dat"), xml.toString());
        pilots.writes = 0;
    }

    private String rosterPilot(int index, String id, String cls, boolean active, boolean dropped) {
        return "<pilot index=\"" + index + "\"><primary_id>" + id + "</primary_id><secondary_id/>"
                + "<comp_id>1</comp_id><name>" + id + "</name><addr1/><addr2/><airplane/><comments/>"
                + "<active>" + active + "</active><dropped>" + dropped + "</dropped><freestyle>false</freestyle>"
                + "<spread_spectrum>false</spread_spectrum><frequency>0</frequency><classes><class>"
                + cls + "</class></classes></pilot>";
    }

    private PilotScores ready(String id) {
        PilotScores s = new PilotScores(id, id, "SPORTSMAN", 2);
        s.setActiveRound("KNOWN", 3); s.setActiveSequence("KNOWN", 2);
        s.setActiveRound("UNKNOWN", 5); s.setActiveSequence("UNKNOWN", 1);
        s.getScores().add(new PScore(3, 1, new float[]{9.5f, 0, -1, -2, -3}, "KNOWN"));
        s.getScores().add(new PScore(4, 1, new float[]{8}, "UNKNOWN"));
        s.setActiveRound("FREESTYLE", 2); s.setActiveSequence("FREESTYLE", 1);
        s.getScores().add(new PScore(1, 1, new float[]{Math.floorMod(id.hashCode(), 10)}, "FREESTYLE"));
        return s;
    }

    private void writeScores(String id, PilotScores s) throws IOException {
        Files.writeString(data.resolve("pilots/scores/" + id + ".json"), gson.toJson(s));
    }
    private Pilot pilot(String id) throws Exception { return pilots.getPilot(id); }
    private PilotScores read(String id) throws Exception { return pilots.getLocalPilotScores(pilot(id)); }

    @Test void previewIsReadOnlyAndApplyPreservesAllScoresForOneAndSixPilots() throws Exception {
        for (int count : List.of(1, 6)) {
            setup(); fixture(count);
            Map<String, String> before = new HashMap<>();
            for (int i = 0; i < count; i++) {
                before.put("p" + i, Files.readString(data.resolve("pilots/scores/p" + i + ".json")));
            }
            assertEquals(true, service.previewKnownRoundCompletion().get("viable"));
            for (var entry : before.entrySet()) {
                assertEquals(entry.getValue(), Files.readString(data.resolve("pilots/scores/" + entry.getKey() + ".json")));
            }
            assertEquals(0, pilots.writes);
            Map<String, Object> result = service.applyKnownRoundCompletion(7);
            assertEquals("ok", result.get("result"));
            assertEquals(count, result.get("pilotsAdvancedCount"));
            assertNull(rounds.getScoringRound());
            assertNull(new RoundsService().getScoringRound());
            assertEquals(2, comp.getComp().getSequences());
            for (int i = 0; i < count; i++) {
                PilotScores s = read("p" + i);
                assertEquals(4, s.getActiveRound("KNOWN"));
                assertEquals(1, s.getActiveSequence("KNOWN"));
                assertEquals(5, s.getActiveRound("UNKNOWN"));
                JsonObject expected = JsonParser.parseString(before.get("p" + i)).getAsJsonObject();
                expected.getAsJsonObject("activeRoundByType").addProperty("KNOWN", 4);
                expected.getAsJsonObject("activeSequenceByType").addProperty("KNOWN", 1);
                assertEquals(expected, JsonParser.parseString(Files.readString(data.resolve("pilots/scores/p" + i + ".json"))));
            }
            assertEquals("validation", service.applyKnownRoundCompletion(7).get("failureKind"));
            assertEquals(count, pilots.writes);
        }
    }

    @Test void partiallyFlownAndAnySavedSequenceTwoAreIneligible() throws Exception {
        PilotScores s = ready("p1"); s.getScores().remove(0); s.setActiveSequence("KNOWN", 1);
        writeScores("p1", s);
        assertEquals(false, service.previewKnownRoundCompletion().get("viable"));
        for (float value : new float[]{0, 9.5f}) {
            s = ready("p1"); s.getScores().add(new PScore(3, 2, new float[]{value}, "KNOWN"));
            s.setActiveRound("KNOWN", 4); s.setActiveSequence("KNOWN", 1); writeScores("p1", s);
            assertEquals(false, service.previewKnownRoundCompletion().get("viable"));
            assertEquals("validation", service.applyKnownRoundCompletion(7).get("failureKind"));
        }
        assertEquals(0, pilots.writes);
    }

    @Test void staleRoundAndChangedEligibilityRejectBeforeWrites() throws Exception {
        assertEquals(true, service.previewKnownRoundCompletion().get("viable"));
        assertEquals("validation", service.applyKnownRoundCompletion(8).get("failureKind"));
        PilotScores s = read("p1"); s.setActiveSequence("KNOWN", 1); writeScores("p1", s);
        assertEquals("validation", service.applyKnownRoundCompletion(7).get("failureKind"));
        assertEquals(0, pilots.writes);
    }

    @Test void simultaneousConfirmationsAdvanceOnlyOnce() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.applyKnownRoundCompletion(7));
            var second = executor.submit(() -> service.applyKnownRoundCompletion(7));
            assertEquals(Set.of("ok", "fail"), Set.of(first.get().get("result"), second.get().get("result")));
            assertEquals(2, pilots.writes);
            assertEquals(4, read("p0").getActiveRound("KNOWN"));
        } finally { executor.shutdownNow(); }
    }

    @Test void completedSingleSequenceCohortDoesNotCreateMissingSequenceMismatch() throws Exception {
        assertEquals("ok", service.applyKnownRoundCompletion(7).get("result"));
        ScoreResolverService resolver = new ScoreResolverService();
        ReflectionTestUtils.setField(resolver, "pilotService", pilots);
        assertEquals(List.of(), resolver.getMismatches().get("mismatches"));
        assertNull(resolver.findUnresolvedMissingSeq2Round(pilot("p0"), pilots.getPilots(true)));
    }

    @Test void inactiveDroppedAndOtherClassesAreNotCohortMembers() throws Exception {
        Files.writeString(data.resolve("pilots.dat"), "<score><pilots>"
                + rosterPilot(0, "p0", "SPORTSMAN", true, false)
                + rosterPilot(1, "absent1", "SPORTSMAN", false, false)
                + rosterPilot(2, "absent2", "SPORTSMAN", true, true)
                + rosterPilot(3, "absent3", "BASIC", true, false) + "</pilots></score>");
        assertEquals(1, service.previewKnownRoundCompletion().get("activePilotCount"));
        assertEquals("ok", service.applyKnownRoundCompletion(7).get("result"));
        assertEquals(1, pilots.writes);
    }

    @Test void missingAndMalformedDataFailWithoutCreationOrNetwork() throws Exception {
        Path file = data.resolve("pilots/scores/p1.json");
        Files.delete(file);
        assertEquals("read", service.previewKnownRoundCompletion().get("failureKind"));
        assertFalse(Files.exists(file));
        for (String json : List.of("{", "null", "{}", gson.toJson(ready("wrong")),
                gson.toJson(ready("p1")).replace("\"KNOWN\":3", "\"KNOWN\":null"),
                gson.toJson(ready("p1")).replace("\"round\":3", "\"round\":3.5"),
                gson.toJson(ready("p1")).replace("9.5", "null"),
                gson.toJson(ready("p1")).replace("9.5", "11"),
                gson.toJson(ready("p1")).replace("9.5", "-4"),
                gson.toJson(ready("p1")).replace("9.5", "1e100"),
                gson.toJson(ready("p1")).replace("9.5", "\"9.5\""),
                gson.toJson(ready("p1")).replace("\"round\":3", "\"round\":0"),
                gson.toJson(ready("p1")).replace("\"sequence\":1", "\"sequence\":3"),
                gson.toJson(ready("p1")).replace("\"type\":\"KNOWN\"", "\"type\":\"INVALID\""),
                gson.toJson(ready("p1")).replace("\"primary_id\":\"p1\"", "\"primary_id\":\"p1\",\"primary_id\":\"p1\""),
                gson.toJson(ready("p1")) + " {}")) {
            Files.writeString(file, json);
            assertEquals("read", service.applyKnownRoundCompletion(7).get("failureKind"), json);
        }
        Files.delete(data.resolve("pilots.dat"));
        assertEquals("read", service.previewKnownRoundCompletion().get("failureKind"));
        assertFalse(Files.exists(data.resolve("pilots.dat")));
        assertEquals(0, pilots.writes);
    }

    @Test void duplicateRecordsAndRosterEntriesFail() throws Exception {
        PilotScores s = ready("p1"); s.getScores().add(s.getScores().get(0)); writeScores("p1", s);
        assertEquals("read", service.previewKnownRoundCompletion().get("failureKind"));
        fixture(2);
        Path roster = data.resolve("pilots.dat");
        Files.writeString(roster, Files.readString(roster).replace("p1", "p0"));
        assertEquals("read", service.previewKnownRoundCompletion().get("failureKind"));
        assertEquals(0, pilots.writes);
    }

    @Test void wrongModesTypesAndEmptyCohortDoNotComplete() throws Exception {
        comp.getComp().setScore_mode("global");
        assertEquals(false, service.previewKnownRoundCompletion().get("viable"));
        comp.getComp().setScore_mode("byRound"); comp.getComp().setSequences(1);
        assertEquals(false, service.previewKnownRoundCompletion().get("viable"));
        comp.getComp().setSequences(2);
        for (String type : List.of("UNKNOWN", "FREESTYLE")) {
            rounds.getScoringRound().setType(type);
            assertEquals(false, service.previewKnownRoundCompletion().get("viable"));
        }
        rounds.getScoringRound().setType("KNOWN"); fixture(0);
        assertEquals(false, service.previewKnownRoundCompletion().get("viable"));
    }

    @Test void pilotWriteFailureStopsAndRequiresRecovery() throws Exception {
        fixture(6); pilots.failAt = 2;
        Map<String, Object> result = service.applyKnownRoundCompletion(7);
        assertEquals("storage", result.get("failureKind")); assertEquals(true, result.get("recoveryRequired"));
        assertEquals(2, pilots.writes);
        assertEquals(4, read("p0").getActiveRound("KNOWN"));
        assertEquals(3, read("p2").getActiveRound("KNOWN"));
        assertNotNull(rounds.getScoringRound());
    }

    @Test void roundCloseFailureIsNotSuccessEvenAfterMemoryChanges() throws Exception {
        RoundsService failing = new RoundsService() {
            @Override public boolean saveRoundsToFile() { return false; }
        };
        KnownRoundCompletionService operation = new KnownRoundCompletionService(comp, pilots, failing);
        assertEquals(true, operation.applyKnownRoundCompletion(7).get("recoveryRequired"));
        assertNull(failing.getScoringRound());
        assertNotNull(new RoundsService().getScoringRound());
    }

    @Test void ordinarySavesStillAdvanceSequenceThenRoundWithoutCompletion() throws Exception {
        PilotScores s = ready("p0"); s.getScores().remove(0); s.setActiveSequence("KNOWN", 1); writeScores("p0", s);
        PilotScoreDTO request = gson.fromJson("{\"primary_id\":\"p0\",\"round\":3,\"sequence\":1,\"type\":\"KNOWN\",\"scores\":[9]}", PilotScoreDTO.class);
        assertEquals(2, pilots.submitScore(request).getActiveSequence("KNOWN"));
        assertNotNull(rounds.getScoringRound());
        request = gson.fromJson("{\"primary_id\":\"p0\",\"round\":3,\"sequence\":2,\"type\":\"KNOWN\",\"scores\":[9]}", PilotScoreDTO.class);
        assertEquals(4, pilots.submitScore(request).getActiveRound("KNOWN"));
        assertEquals(2, read("p1").getActiveSequence("KNOWN"));
        assertEquals(3, read("p1").getActiveRound("KNOWN"));
    }

    @Test void ordinaryDoubleSequenceRoundStillClosesThroughPilotList() throws Exception {
        fixture(1);
        PilotScoreDTO request = gson.fromJson("{\"primary_id\":\"p0\",\"round\":3,\"sequence\":2,\"type\":\"KNOWN\",\"scores\":[9]}", PilotScoreDTO.class);
        pilots.submitScore(request);
        RootController root = new RootController();
        ReflectionTestUtils.setField(root, "compService", comp);
        ReflectionTestUtils.setField(root, "roundService", rounds);
        ReflectionTestUtils.setField(root, "pilotService", pilots);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        assertEquals("redirect:/round-complete", root.pilotListRound(new ExtendedModelMap(), null, redirect));
        assertEquals("Sportsman Known Round 3", redirect.getFlashAttributes().get("completedRoundLabel"));
        assertEquals(1, redirect.getFlashAttributes().get("completedPilotCount"));
        assertFalse(redirect.getFlashAttributes().containsKey("singleSequenceCompleted"));
        assertNull(rounds.getScoringRound());
        assertEquals("redirect:/newround", root.home(new ExtendedModelMap()));
    }

    @Test void deferredPilotRemainsAvailableUntilTheirSecondSequenceForTwoAndSixPilots() throws Exception {
        for (int count : List.of(2, 6)) {
            setup(); fixture(count);
            for (int i = 1; i < count; i++) {
                PilotScoreDTO request = gson.fromJson("{\"primary_id\":\"p" + i
                        + "\",\"round\":3,\"sequence\":2,\"type\":\"KNOWN\",\"scores\":[0]}", PilotScoreDTO.class);
                pilots.submitScore(request);
            }
            assertEquals(false, service.previewKnownRoundCompletion().get("viable"));
            RootController root = new RootController();
            ReflectionTestUtils.setField(root, "compService", comp);
            ReflectionTestUtils.setField(root, "roundService", rounds);
            ReflectionTestUtils.setField(root, "pilotService", pilots);
            ReflectionTestUtils.setField(root, "settingService", new SettingService() {
                @Override public SettingDTO getSettings() { return new SettingDTO(); }
            });
            ReflectionTestUtils.setField(root, "scheduleService", new ScheduleService().setSchedules(Map.of()));
            ExtendedModelMap model = new ExtendedModelMap();
            assertEquals("pilot-list-round", root.pilotListRound(model, null, new RedirectAttributesModelMap()));
            assertEquals(List.of("p0"), ((List<?>) model.get("pilots")).stream()
                    .map(p -> ((Pilot) p).getPrimary_id()).toList());
            assertEquals(2, read("p0").getActiveSequence("KNOWN"));
            assertNotNull(rounds.getScoringRound());
            pilots.submitScore(gson.fromJson("{\"primary_id\":\"p0\",\"round\":3,\"sequence\":2,\"type\":\"KNOWN\",\"scores\":[8]}", PilotScoreDTO.class));
            assertEquals("redirect:/round-complete", root.pilotListRound(new ExtendedModelMap(), null, new RedirectAttributesModelMap()));
            assertNull(rounds.getScoringRound());
        }
    }

    @Test void httpHandoffIsSuccessfulOnlyAndConsumedOnce() throws Exception {
        APIController api = new APIController();
        ReflectionTestUtils.setField(api, "knownRoundCompletionService", service);
        RootController root = new RootController();
        ReflectionTestUtils.setField(root, "compService", comp);
        ReflectionTestUtils.setField(root, "roundService", rounds);
        ReflectionTestUtils.setField(root, "settingService", new SettingService() {
            @Override public SettingDTO getSettings() { return new SettingDTO(); }
        });
        MockMvc mvc = MockMvcBuilders.standaloneSetup(api, root)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .setViewResolvers(new InternalResourceViewResolver("/views/", ".html")).build();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/scores/rbr-known-round-completion/apply").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRoundId\":8}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/scores/rbr-known-round-completion/apply").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRoundId\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roundClosed").value(true));
        // If navigation is lost, an unrelated ordinary completion must not consume this handoff.
        mvc.perform(get("/round-complete").session(session))
                .andExpect(model().attribute("singleSequenceCompleted", false))
                .andExpect(model().attribute("completedRoundLabel", "Round Complete"));
        mvc.perform(get("/round-complete?completedRoundId=8").session(session))
                .andExpect(model().attribute("singleSequenceCompleted", false));
        mvc.perform(get("/round-complete?completedRoundId=7").session(session)).andExpect(status().isOk())
                .andExpect(model().attribute("completedRoundLabel", "Sportsman Known Round 3"))
                .andExpect(model().attribute("completedPilotCount", 2))
                .andExpect(model().attribute("singleSequenceCompleted", true));
        mvc.perform(get("/round-complete?completedRoundId=7").session(session))
                .andExpect(model().attribute("singleSequenceCompleted", false));
        mvc.perform(post("/api/scores/rbr-known-round-completion/apply").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRoundId\":7}"))
                .andExpect(status().isConflict());
    }

    private static class CountingPilots extends PilotService {
        int writes;
        int failAt = -1;
        @Override public void getPilotsFileFromScore() { throw new AssertionError("Network must not be used"); }
        @Override public PilotScores savePilotScoresToFile(PilotScores scores) throws IOException {
            writes++;
            if (writes == failAt) throw new IOException("Injected write failure");
            return super.savePilotScoresToFile(scores);
        }
    }
}
