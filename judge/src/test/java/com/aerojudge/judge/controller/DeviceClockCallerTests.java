package com.aerojudge.judge.controller;

import com.aerojudge.judge.dto.CompDTO;
import com.aerojudge.judge.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceClockCallerTests {
    @Test void eventLoadAndEditBothRequestClockSyncWithoutChangingDataFlow() throws Exception {
        for (boolean edit : new boolean[]{false, true}) {
            APIController controller = new APIController();
            PilotService pilots = mock(PilotService.class);
            CompService comps = mock(CompService.class);
            SequenceService sequences = mock(SequenceService.class);
            ScheduleService schedules = mock(ScheduleService.class);
            SettingService settings = mock(SettingService.class);
            RoundsService rounds = mock(RoundsService.class);
            ReflectionTestUtils.setField(controller, "pilotService", pilots);
            ReflectionTestUtils.setField(controller, "compService", comps);
            ReflectionTestUtils.setField(controller, "sequenceService", sequences);
            ReflectionTestUtils.setField(controller, "scheduleService", schedules);
            ReflectionTestUtils.setField(controller, "settingService", settings);
            ReflectionTestUtils.setField(controller, "roundsService", rounds);
            CompDTO comp = new CompDTO();
            when(comps.createCompFromRequest(comp)).thenReturn(comp);
            when(rounds.resetRoundsForComp(anyInt())).thenReturn(true);
            assertEquals(201, controller.createComp(comp, edit).getStatusCode().value());
            verify(pilots).getPilotsFileFromScore(true);
            verify(pilots, never()).getPilotsFileFromScore();
            verify(sequences).getSequenceFileFromScore();
            verify(schedules).populateSequences();
            verify(settings, times(edit ? 0 : 1)).backupAllFiles();
        }
    }

    @Test void explicitPilotAndValidationRefreshRequestClockSync() throws Exception {
        PilotService pilots = mock(PilotService.class);
        SequenceService sequences = mock(SequenceService.class);
        ScheduleService schedules = mock(ScheduleService.class);
        APIController api = new APIController();
        ValidationController validation = new ValidationController();
        for (Object controller : new Object[]{api, validation}) {
            ReflectionTestUtils.setField(controller, "pilotService", pilots);
            ReflectionTestUtils.setField(controller, "sequenceService", sequences);
            ReflectionTestUtils.setField(controller, "scheduleService", schedules);
        }
        assertEquals("{\"sync\":\"ok\"}", api.syncPilots());
        assertEquals("redirect:/validate-sequences", validation.resync(new RedirectAttributesModelMap()));
        verify(pilots, times(2)).getPilotsFileFromScore(true);
        verify(pilots, never()).getPilotsFileFromScore();
        verify(sequences, times(2)).getSequenceFileFromScore();
        verify(schedules, times(2)).populateSequences();
    }
}
