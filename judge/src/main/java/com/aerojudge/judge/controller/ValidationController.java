package com.aerojudge.judge.controller;

import com.aerojudge.judge.dto.ValidationResult;
import com.aerojudge.judge.service.CompService;
import com.aerojudge.judge.service.PilotService;
import com.aerojudge.judge.service.ScheduleService;
import com.aerojudge.judge.service.SequenceService;
import com.aerojudge.judge.service.SequenceValidationService;
import com.aerojudge.judge.service.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.xml.sax.SAXException;

import jakarta.servlet.http.HttpSession;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * Controller for sequence validation flow.
 * Validates sequence configurations after sync and before judging can begin.
 */
@Controller
public class ValidationController {

    private static final Logger logger = LoggerFactory.getLogger(ValidationController.class);

    @Autowired
    private SequenceValidationService validationService;

    @Autowired
    private SettingService settingService;

    @Autowired
    private PilotService pilotService;

    @Autowired
    private SequenceService sequenceService;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private CompService compService;

    /**
     * Called after sync completes. Validates all sequences and redirects appropriately.
     * If issues found, shows the error screen.
     * If clean, redirects to normal flow.
     */
    @GetMapping("/validate-sequences")
    public String validateSequences(Model model) throws IOException, ParserConfigurationException, SAXException {
        logger.info("Running sequence validation...");

        ValidationResult result = validationService.validateAll();

        model.addAttribute("settings", settingService.getSettings());
        model.addAttribute("compName", compService.getComp().getComp_name());
        model.addAttribute("issues", result.getIssues());
        model.addAttribute("hasErrors", result.hasErrors());
        model.addAttribute("hasWarnings", result.hasWarnings());
        model.addAttribute("errorCount", result.getTotalErrorCount());
        model.addAttribute("warningCount", result.getTotalWarningCount());
        model.addAttribute("isSuccess", !result.hasErrors() && !result.hasWarnings());

        if (!result.hasErrors() && !result.hasWarnings()) {
            logger.info("Validation passed - showing success page");
        }

        // Always show validation page - user clicks to continue
        return "sequence_validation";
    }

    /**
     * User chose to continue despite validation errors.
     * Sets a session flag and redirects to normal flow.
     */
    @PostMapping("/validate-sequences/continue")
    public String continueAnyway(HttpSession session) {
        logger.warn("User chose to continue despite validation errors");
        session.setAttribute("validationOverridden", true);
        return "redirect:/rounds";
    }

    /**
     * User requested to re-sync from the validation error screen.
     * Performs sync directly and re-validates (does NOT go to admin page).
     */
    @PostMapping("/validate-sequences/resync")
    public String resync(RedirectAttributes redirectAttributes) {
        logger.info("User requested re-sync from validation error screen");
        try {
            // Perform the sync directly
            pilotService.getPilotsFileFromScore(true);
            sequenceService.getSequenceFileFromScore();

            // IMPORTANT: Force reload the schedules cache with new data!
            // Without this, validation would use stale cached data
            scheduleService.populateSequences();

            //now try to fetch unknown figures if there are UNKNOWN sequences
            try {
                if (sequenceService.hasUnknownSequences()) {
                    sequenceService.getUnknownFiguresZip();
                } else {
                    logger.info("No UNKNOWN sequences found, skipping unknown figures download");
                }
            } catch (Exception e) {
                logger.warn("Failed to download unknown figures: {}", e.getMessage());
            }       
            
            logger.info("Re-sync completed successfully - schedules reloaded");
        } catch (Exception e) {
            logger.error("Re-sync failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("syncError", "Sync failed: " + e.getMessage());
        }
        // Redirect back to validation to show new results
        return "redirect:/validate-sequences";
    }
}
