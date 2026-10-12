package com.aerojudge.judge.controller;

import com.aerojudge.judge.service.*;
import com.aerojudge.judge.utils.DeviceClockWarningInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeviceClockWarningTests {
    DeviceClockService clock;
    RootController root;
    ValidationController validation;
    CompService comp;
    AtomicBoolean pending;
    MockMvc mvc;

    @BeforeEach void setup() {
        clock = mock(DeviceClockService.class);
        pending = new AtomicBoolean(true);
        when(clock.isTimeLossWarningPending()).thenAnswer(call -> pending.get());
        doAnswer(call -> { pending.set(false); return null; }).when(clock).dismissTimeLossWarning();
        comp = mock(CompService.class);
        root = new RootController();
        validation = new ValidationController();
        ReflectionTestUtils.setField(root, "deviceClockService", clock);
        ReflectionTestUtils.setField(root, "compService", comp);
        mvc = MockMvcBuilders.standaloneSetup(root, validation)
                .addInterceptors(new DeviceClockWarningInterceptor(clock))
                .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
                .build();
    }

    @Test void everyExistingPageIsInterceptedBeforeSideEffectsAndProbesDoNotConsumeWarning() throws Exception {
        for (Class<?> controller : new Class<?>[]{RootController.class, ValidationController.class}) {
            for (var method : controller.getDeclaredMethods()) {
                GetMapping mapping = method.getAnnotation(GetMapping.class);
                if (mapping == null) continue;
                for (String route : mapping.value()) {
                    if ("/device-time-warning".equals(route)) continue;
                    mvc.perform(get(route)).andExpect(status().isFound())
                            .andExpect(redirectedUrl("/device-time-warning"));
                }
            }
        }
        mvc.perform(get("/")).andExpect(redirectedUrl("/device-time-warning"));
        assertTrue(pending.get());
        verifyNoInteractions(comp);
        verify(clock, never()).dismissTimeLossWarning();
        verify(clock, never()).resyncClockFromScore();
    }

    @Test void dismissRedirectsThroughRootWithoutScoreAndStaysDismissed() throws Exception {
        mvc.perform(get("/device-time-warning")).andExpect(status().isOk())
                .andExpect(view().name("device-time-warning"));
        mvc.perform(post("/device-time-warning/dismiss"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/")).andExpect(redirectedUrl("/newcomp"));
        mvc.perform(get("/device-time-warning")).andExpect(redirectedUrl("/"));
        verify(clock, never()).resyncClockFromScore();
    }

    @Test void resyncReportsVerifiedOutcomeAndLeavesFailedWarningPending() throws Exception {
        when(clock.resyncClockFromScore()).thenReturn(Map.of("success", false, "message", "RTC write failed"));
        mvc.perform(post("/device-time-warning/resync"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("RTC write failed"));
        assertTrue(pending.get());
        when(clock.resyncClockFromScore()).thenAnswer(call -> {
            pending.set(false);
            return Map.of("success", true, "message", "Clock synchronized with Score");
        });
        mvc.perform(post("/device-time-warning/resync"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/device-time-warning")).andExpect(redirectedUrl("/"));
    }

    @Test void assetsHealthApiAndPostHandlersAreExcluded() throws Exception {
        DeviceClockWarningInterceptor interceptor = new DeviceClockWarningInterceptor(clock);
        for (Object handler : new Object[]{new Object(),
                new HandlerMethod(new APIController(), APIController.class.getMethod("checkForUpdate"))}) {
            HttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/check-update");
            assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), handler));
        }
        HandlerMethod page = new HandlerMethod(root, RootController.class.getMethod("home", Model.class));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", "/"),
                new MockHttpServletResponse(), page));
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mvc.perform(get("/materialize/css/materialize.min.css")).andExpect(status().isNotFound());
        verify(clock, never()).resyncClockFromScore();
    }
}
