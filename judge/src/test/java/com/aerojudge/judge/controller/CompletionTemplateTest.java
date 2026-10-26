package com.aerojudge.judge.controller;

import com.aerojudge.judge.dto.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import static org.junit.jupiter.api.Assertions.*;

class CompletionTemplateTest {
    @Test void renderActualTemplatesForBrowserVerification() throws Exception {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
        SpringTemplateEngine engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        MockServletContext servlet = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servlet);
        request.setRequestURI("/judge");
        WebContext context = new WebContext(JakartaServletWebApplication.buildApplication(servlet)
                .buildExchange(request, new MockHttpServletResponse()), Locale.ENGLISH);
        Pilot pilot = new Pilot(false, "", "", "", "SPORTSMAN", 0, true, 1, 0, false, "", "", "Test Pilot", false, "p0");
        List<FigureDTO> figures = new ArrayList<>();
        for (int i = 1; i <= 12; i++) figures.add(new FigureDTO(i, 10, "Test figure " + i, "HALF"));
        SettingDTO settings = new SettingDTO();
        context.setVariable("settings", settings);
        context.setVariable("pilot", pilot); context.setVariable("pilot_class", "SPORTSMAN");
        context.setVariable("maneuvers", figures); context.setVariable("numOfManeuvers", figures.size());
        context.setVariable("sequencesjson", new Gson().toJson(figures));
        context.setVariable("activeRound", 3); context.setVariable("activeSequence", 2);
        context.setVariable("roundType", "KNOWN"); context.setVariable("scoreMode", "byRound");
        context.setVariable("judgingRoundId", 7); context.setVariable("sequenceType", "std");
        context.setVariable("sequenceFolderPath", "SPORTSMAN/KNOWN"); context.setVariable("dirletter", "B");
        Path output = Paths.get("target/completion-browser"); Files.createDirectories(output);
        String judge = engine.process("judge", context);
        assertTrue(judge.contains("const judgingRoundId = 7"));
        Files.writeString(output.resolve("judge.html"), judge);
        context.setVariable("activeSequence", 1);
        Files.writeString(output.resolve("judge-sequence-one.html"), engine.process("judge", context));
        context.setVariable("activeSequence", 2);
        context.setVariable("scoreMode", "global");
        Files.writeString(output.resolve("judge-global.html"), engine.process("judge", context));
        context.setVariable("scoreMode", "byRound");
        context.setVariable("roundType", "UNKNOWN");
        Files.writeString(output.resolve("judge-unknown.html"), engine.process("judge", context));
        context.setVariable("completedRoundLabel", "Sportsman Known Round 3");
        context.setVariable("completedPilotCount", 2);
        for (boolean autoSync : List.of(false, true)) {
            context.setVariable("attemptAutoSyncScores", autoSync);
            for (boolean completed : List.of(false, true)) {
                context.setVariable("singleSequenceCompleted", completed);
                String html = engine.process("round-complete", context);
                assertTrue(html.contains("const singleSequenceCompleted = " + completed));
                assertTrue(html.contains("const attemptAutoSyncScores = " + autoSync));
                Files.writeString(output.resolve("complete-" + autoSync + "-" + completed + ".html"), html);
            }
            context.setVariable("lineNumber", 1);
            context.setVariable("judgeId", 3);
            for (String mode : List.of("byRound", "global")) {
                context.setVariable("scoreMode", mode);
                String html = engine.process("adminDevice", context);
                assertTrue(html.contains("const attemptAutoSyncScores = " + autoSync));
                Files.writeString(output.resolve("admin-" + mode + "-" + autoSync + ".html"), html);
            }
        }
    }
}
