package com.aerojudge.judge.service;

import com.aerojudge.judge.controller.APIController;
import com.aerojudge.judge.controller.RootController;
import com.aerojudge.judge.dto.CompDTO;
import com.aerojudge.judge.utils.SettingUtils;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AutoSyncPreferenceTest {
    @TempDir static Path data;
    private LocalComp comp;
    private SettingService settings;
    private RoundsService rounds;
    private APIController api;
    private MockMvc mvc;
    private int guardCalls;
    private boolean activeRound;
    private final Gson gson = new Gson();

    @BeforeEach void setup() throws Exception {
        SettingUtils.setApplicationConfigPath(data.toString());
        Files.writeString(data.resolve("settings.json"), "{\"judge_id\":3,\"language\":\"en\",\"attempt_auto_sync_scores\":true}");
        Files.writeString(data.resolve("comp.json"), "{\"comp_id\":1,\"comp_name\":\"Test\",\"score_mode\":\"byRound\",\"sequences\":2}");
        Files.writeString(data.resolve("rounds.json"), "{\"comp_id\":1,\"rounds\":[]}");
        comp = new LocalComp();
        comp.getCompFromFile();
        settings = new SettingService();
        rounds = new RoundsService() {
            @Override public boolean isScoringRound() { return activeRound; }
        };
        api = new APIController();
        ReflectionTestUtils.setField(api, "compService", comp);
        ReflectionTestUtils.setField(api, "roundsService", rounds);
        ReflectionTestUtils.setField(api, "scoreResolverService", new ScoreResolverService() {
            @Override public Map<String, Object> evaluateFormatChangeBlock() {
                guardCalls++;
                return Map.of("result", "fail", "blocker", "format_change_unsafe");
            }
        });
        // Event endpoints still exercise real comp persistence; external Score and archive work are isolated.
        ReflectionTestUtils.setField(api, "pilotService", new PilotService() {
            @Override public void getPilotsFileFromScore() {}
            @Override public void setupPilotScores() {}
        });
        ReflectionTestUtils.setField(api, "settingService", new SettingService() {
            @Override public void backupAllFiles() {}
        });
        ReflectionTestUtils.setField(api, "sequenceService", new SequenceService() {
            @Override public void getSequenceFileFromScore() {}
            @Override public boolean hasUnknownSequences() { return false; }
        });
        ReflectionTestUtils.setField(api, "scheduleService", new ScheduleService() {
            @Override public void populateSequences() {}
        });
        mvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test void defaultsIgnoreLegacyWithoutRewritingFiles() throws Exception {
        assertEquals(false, comp.getComp().getAttempt_auto_sync_scores());
        assertFalse(Files.readString(data.resolve("comp.json")).contains("attempt_auto_sync_scores"));
        assertTrue(Files.readString(data.resolve("settings.json")).contains("attempt_auto_sync_scores"));
        assertFalse(gson.toJson(settings.getSettings()).contains("attempt_auto_sync_scores"));
        settings.updateSettings(settings.getSettings());
        assertFalse(Files.readString(data.resolve("settings.json")).contains("attempt_auto_sync_scores"));
        assertFalse(gson.toJson(settings.createSettings()).contains("attempt_auto_sync_scores"));
        Files.delete(data.resolve("comp.json"));
        assertEquals(false, new LocalComp().createCompFromRequest(event()).getAttempt_auto_sync_scores());
    }

    @Test void localSaveRetainsOmittedPreferenceAndPersistsExplicitOff() throws Exception {
        String settingsBefore = Files.readString(data.resolve("settings.json"));
        for (boolean value : new boolean[]{true, false}) {
            mvc.perform(post("/api/comp/local").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"score_mode\":\"byRound\",\"attempt_auto_sync_scores\":" + value + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("ok"));
            assertEquals(value, new LocalComp().getCompFromFile().getAttempt_auto_sync_scores());
            mvc.perform(post("/api/comp/local").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sequences\":2,\"sequenceType\":\"alt\"}"))
                    .andExpect(status().isOk());
            assertEquals(value, new LocalComp().getCompFromFile().getAttempt_auto_sync_scores());
        }
        assertEquals(4, comp.localWrites);
        assertEquals(0, guardCalls);
        assertEquals(settingsBefore, Files.readString(data.resolve("settings.json")));
    }

    @Test void refreshAndNewEventRequestsRetainBothValues() throws Exception {
        for (boolean value : new boolean[]{true, false}) {
            comp.getComp().setAttempt_auto_sync_scores(value);
            comp.saveCompToFileLocal();
            for (boolean edit : new boolean[]{true, false}) {
                mvc.perform(post("/api/comp").param("edit", String.valueOf(edit))
                        .contentType(MediaType.APPLICATION_JSON).content(gson.toJson(event())))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.result").value("ok"));
                assertEquals(value, new LocalComp().getCompFromFile().getAttempt_auto_sync_scores());
            }
            // Same omitted payload as event loading, starting without a cached competition.
            assertEquals(value, new LocalComp().createCompFromRequest(event()).getAttempt_auto_sync_scores());
        }
        comp.getComp().setAttempt_auto_sync_scores(true);
        CompDTO explicitOff = event();
        explicitOff.setAttempt_auto_sync_scores(false);
        assertEquals(false, comp.createCompFromRequest(explicitOff).getAttempt_auto_sync_scores());
    }

    @Test void guardsRejectWithoutChangingPreferenceButIgnoreOmittedSequenceCount() throws Exception {
        comp.getComp().setAttempt_auto_sync_scores(true);
        activeRound = true;
        mvc.perform(post("/api/comp/local").contentType(MediaType.APPLICATION_JSON)
                .content("{\"score_mode\":\"global\",\"attempt_auto_sync_scores\":false}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/comp/local").contentType(MediaType.APPLICATION_JSON)
                .content("{\"score_mode\":\"invalid\",\"attempt_auto_sync_scores\":false}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, guardCalls);
        mvc.perform(post("/api/comp/local").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sequences\":1,\"attempt_auto_sync_scores\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.blocker").value("format_change_unsafe"));
        assertEquals(1, guardCalls);
        assertEquals(0, comp.localWrites);
        assertEquals(true, comp.getComp().getAttempt_auto_sync_scores());
    }

    @Test void pageModelsRetainSelectionInGlobalButOnlyEnableRbrCompletion() throws Exception {
        RootController root = new RootController();
        ReflectionTestUtils.setField(root, "compService", comp);
        ReflectionTestUtils.setField(root, "settingService", settings);
        ReflectionTestUtils.setField(root, "roundService", rounds);
        for (String mode : new String[]{"global", "byRound"}) {
            mvc.perform(post("/api/comp/local").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"score_mode\":\"" + mode + "\",\"attempt_auto_sync_scores\":true}"))
                    .andExpect(status().isOk());
            ExtendedModelMap admin = new ExtendedModelMap(), complete = new ExtendedModelMap();
            assertEquals("adminDevice", root.adminDevice(admin));
            assertEquals(true, admin.get("attemptAutoSyncScores"));
            assertEquals("round-complete", root.roundComplete(complete));
            assertEquals(mode.equals("byRound"), complete.get("attemptAutoSyncScores"));
        }
        Files.delete(data.resolve("comp.json"));
        ReflectionTestUtils.setField(root, "compService", new LocalComp());
        ExtendedModelMap empty = new ExtendedModelMap();
        assertEquals("adminDevice", root.adminDevice(empty));
        assertEquals(false, empty.get("attemptAutoSyncScores"));
    }

    @Test void writeFailurePropagatesWithoutSuccessOrSettingsWrite() throws Exception {
        String before = Files.readString(data.resolve("settings.json"));
        comp.failSave = true;
        CompDTO request = new CompDTO();
        request.setAttempt_auto_sync_scores(true);
        assertThrows(IOException.class, () -> api.updateLocalCompSettings(request));
        assertEquals(1, comp.localWrites);
        assertEquals(before, Files.readString(data.resolve("settings.json")));
    }

    private CompDTO event() {
        CompDTO event = new CompDTO("byRound", 2, 1);
        event.setComp_id(2); event.setComp_name("Next event");
        return event;
    }

    private static class LocalComp extends CompService {
        int localWrites;
        boolean failSave;
        @Override public void enrichCompWithCompInfoFromScore(CompDTO value) {}
        @Override public boolean saveCompToFileLocal() throws IOException {
            localWrites++;
            if (failSave) throw new IOException("Injected save failure");
            return super.saveCompToFileLocal();
        }
    }
}
