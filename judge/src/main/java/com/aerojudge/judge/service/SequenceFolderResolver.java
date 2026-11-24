package com.aerojudge.judge.service;

import com.aerojudge.judge.dto.ScheduleDTO;
import com.aerojudge.judge.utils.SettingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the folder path for figure assets using a 5-case fallback chain.
 *
 * Case 1: short_desc HAS value + KNOWN  -> event/ -> default/ -> derive -> FAIL
 * Case 2: short_desc HAS value + UNKNOWN -> event/ -> default/ -> FAIL
 * Case 3: short_desc BLANK + KNOWN -> derive -> FAIL
 * Case 4: short_desc BLANK + UNKNOWN -> FAIL (cannot derive)
 * Case 5: FREESTYLE -> honor short_desc if present, fallback to default/FS/
 */
@Service
public class SequenceFolderResolver {

    private static final Logger logger = LoggerFactory.getLogger(SequenceFolderResolver.class);

    private static final String FIGURES_PATH_BASE = SettingUtils.getApplicationConfigPath() + "/figures";
    public static final String FAIL_FOLDER = "default/FAIL";
    public static final String FREESTYLE_FOLDER = "default/FS";
    // Release workflow version rule, two-digit year captured
    private static final Pattern FIGURES_VERSION = Pattern.compile("20([0-9]{2})[.](0|[1-9][0-9]*)");

    // Class code mapping (2 chars)
    private static final Map<String, String> CLASS_CODES = Map.of(
        "BASIC", "BA",
        "SPORTSMAN", "SP",
        "INTERMEDIATE", "IN",
        "ADVANCED", "AD",
        "UNLIMITED", "UN",
        "INVITATIONAL", "IV"
    );

    @Autowired
    private ScheduleService scheduleService;

    /**
     * Resolves the folder path for figures using 5-case logic.
     *
     * @param pilotClass   e.g., "SPORTSMAN" (ignored for FREESTYLE)
     * @param roundType    e.g., "KNOWN", "UNKNOWN", "FREESTYLE"
     * @param roundNumber  e.g., 4
     * @param sequenceType e.g., "std" (for fallback variant when short_desc blank)
     * @return folder path relative to /man/, e.g., "default/SPK_27S"
     */
    public String resolve(String pilotClass, String roundType,
                          int roundNumber, String sequenceType) {

        logger.debug("Resolving folder for class={}, type={}, round={}, seqType={}",
                pilotClass, roundType, roundNumber, sequenceType);

        // ===================================================================
        // CASE 5: FREESTYLE - honor short_desc if present, fallback to FS
        // ===================================================================
        if ("FREESTYLE".equalsIgnoreCase(roundType)) {
            ScheduleDTO schedule = findFreestyleSchedule(roundNumber);
            String shortDesc = (schedule != null) ? schedule.getShort_desc() : null;

            if (shortDesc != null && !shortDesc.trim().isEmpty()) {
                shortDesc = shortDesc.trim().toUpperCase();
                // Check event folder first (custom event freestyle)
                if (folderExists("event/" + shortDesc)) {
                    logger.debug("Freestyle resolved to event/{}", shortDesc);
                    return "event/" + shortDesc;
                }
                // Check default folder (software-managed custom freestyle)
                if (folderExists("default/" + shortDesc)) {
                    logger.debug("Freestyle resolved to default/{}", shortDesc);
                    return "default/" + shortDesc;
                }
            }
            // Fallback: Standard freestyle folder (always exists)
            logger.debug("Freestyle using standard folder: {}", FREESTYLE_FOLDER);
            return FREESTYLE_FOLDER;
        }

        // Find matching schedule from sequences.dat
        ScheduleDTO schedule = findSchedule(pilotClass, roundType, roundNumber);
        String shortDesc = (schedule != null) ? schedule.getShort_desc() : null;

        // Normalize short_desc (UPPERCASE, trim, null if empty)
        if (shortDesc != null) {
            shortDesc = shortDesc.trim().toUpperCase();
            if (shortDesc.isEmpty()) shortDesc = null;
        }

        boolean isKnown = "KNOWN".equalsIgnoreCase(roundType);
        boolean hasShortDesc = (shortDesc != null);

        // ===================================================================
        // CASE 4: BLANK + UNKNOWN -> straight to FAIL
        // ===================================================================
        if (!hasShortDesc && !isKnown) {
            logger.warn("Unknown sequence without short_desc - using FAIL folder");
            return FAIL_FOLDER;
        }

        // ===================================================================
        // CASE 3: BLANK + KNOWN -> derive -> FAIL
        // ===================================================================
        if (!hasShortDesc && isKnown) {
            String derived = deriveKnownFolder(pilotClass, sequenceType);
            if (derived != null && folderExists("default/" + derived)) {
                logger.debug("Derived folder for blank short_desc: default/{}", derived);
                return "default/" + derived;
            }
            logger.warn("Could not derive folder for blank short_desc - using FAIL");
            return FAIL_FOLDER;
        }

        // ===================================================================
        // CASES 1 & 2: short_desc HAS value
        // ===================================================================

        // Step 1: Check event folder (exact match)
        if (folderExists("event/" + shortDesc)) {
            logger.debug("Resolved to event folder: event/{}", shortDesc);
            return "event/" + shortDesc;
        }

        // Step 2: Check default folder (exact match)
        if (folderExists("default/" + shortDesc)) {
            logger.debug("Resolved to default folder: default/{}", shortDesc);
            return "default/" + shortDesc;
        }

        // Step 3 (CASE 1 only): KNOWN can try derivation as safety net
        if (isKnown) {
            // Extract variant from short_desc suffix if possible, else use sequenceType
            String variant = extractVariantFromShortDesc(shortDesc, sequenceType);
            String derived = deriveKnownFolder(pilotClass, variant);
            if (derived != null && folderExists("default/" + derived)) {
                logger.info("Recovered from missing short_desc folder {} -> default/{}",
                        shortDesc, derived);
                return "default/" + derived;
            }
        }

        // Step 4: All options exhausted -> FAIL
        logger.warn("All resolution options exhausted for {} - using FAIL folder", shortDesc);
        return FAIL_FOLDER;
    }

    /**
     * Find schedule matching class, type, and round number.
     * Returns the most specific match (highest min_round).
     * If no exact match, falls back to highest max_round (nearest match).
     */
    public ScheduleDTO findSchedule(String pilotClass, String roundType, int roundNumber) {
        logger.info("FolderResolver.findSchedule called: class={}, type={}, round={}",
                pilotClass, roundType, roundNumber);
        Map<Integer, ScheduleDTO> schedules = scheduleService.getSchedules();
        if (schedules == null) {
            logger.warn("Schedules is null!");
            return null;
        }

        ScheduleDTO bestMatch = null;
        int bestMinRound = -1;

        // First pass: Find exact match (round in range)
        for (ScheduleDTO sched : schedules.values()) {
            // Add null checks to prevent NPE
            if (sched.getComp_class() == null || sched.getType() == null) {
                logger.warn("Schedule has null class or type, skipping");
                continue;
            }
            if (sched.getComp_class().equalsIgnoreCase(pilotClass) &&
                sched.getType().equalsIgnoreCase(roundType) &&
                sched.getMin_round() != null && sched.getMax_round() != null &&
                sched.getMin_round() <= roundNumber &&
                sched.getMax_round() >= roundNumber) {

                logger.info("FolderResolver: Found match - min={}, max={}, short_desc={}",
                        sched.getMin_round(), sched.getMax_round(), sched.getShort_desc());

                // Prefer schedule with highest min_round (most specific)
                if (sched.getMin_round() > bestMinRound) {
                    bestMinRound = sched.getMin_round();
                    bestMatch = sched;
                    logger.info("FolderResolver: New best match: min_round={}, short_desc={}",
                            bestMinRound, sched.getShort_desc());
                }
                // Tie-breaker: identical min_round - keep first match
                // Pre-flight validation will have warned about this
            }
        }

        // Second pass: No exact match - find nearest (highest max_round)
        // This handles rounds beyond defined ranges (e.g., round 25 when max is 20)
        if (bestMatch == null) {
            logger.warn("FolderResolver: No exact match, trying fallback...");
            int highestMaxRound = -1;
            for (ScheduleDTO sched : schedules.values()) {
                if (sched.getComp_class() != null && sched.getType() != null &&
                    sched.getComp_class().equalsIgnoreCase(pilotClass) &&
                    sched.getType().equalsIgnoreCase(roundType)) {

                    if (sched.getMax_round() != null && sched.getMax_round() > highestMaxRound) {
                        highestMaxRound = sched.getMax_round();
                        bestMatch = sched;
                    }
                }
            }
            if (bestMatch != null) {
                logger.info("FolderResolver: Fallback match with max_round={}, short_desc={}",
                        highestMaxRound, bestMatch.getShort_desc());
            }
        }

        if (bestMatch != null) {
            logger.info("FolderResolver RESULT: For round {} returning short_desc={}",
                    roundNumber, bestMatch.getShort_desc());
        } else {
            logger.warn("FolderResolver RESULT: No schedule found!");
        }

        return bestMatch;
    }

    /**
     * Find freestyle schedule for round number.
     * Similar to findSchedule but for FREESTYLE type (no class).
     */
    private ScheduleDTO findFreestyleSchedule(int roundNumber) {
        Map<Integer, ScheduleDTO> schedules = scheduleService.getSchedules();
        if (schedules == null) return null;

        for (ScheduleDTO sched : schedules.values()) {
            if ("FREESTYLE".equalsIgnoreCase(sched.getType()) &&
                sched.getMin_round() <= roundNumber &&
                sched.getMax_round() >= roundNumber) {
                return sched;
            }
        }
        return null;  // No freestyle schedule found - will use default/FS/
    }

    /**
     * Derive folder name for KNOWN sequences using the season year of the installed figures package.
     * Example: SPORTSMAN + std -> SPK_27S (figures package 2027.0)
     *
     * @param pilotClass e.g., "SPORTSMAN"
     * @param variantOrSeqType "S", "A", "std", or "alt"
     * @return folder name e.g., "SPK_26S" or null if cannot derive
     */
    private String deriveKnownFolder(String pilotClass, String variantOrSeqType) {
        String classCode = CLASS_CODES.get(pilotClass.toUpperCase());
        if (classCode == null) {
            logger.warn("Unknown pilot class: {}", pilotClass);
            return null;
        }

        // Season year comes from the installed figures package, e.g. "2027.0" -> "27".
        String version = SettingUtils.readPackageVersion(Path.of(FIGURES_PATH_BASE, "package.json"));
        Matcher match = (version != null) ? FIGURES_VERSION.matcher(version) : null;
        if (match == null || !match.matches()) {
            logger.warn("Season year unavailable: figures package version missing or invalid ({})", version);
            return null;
        }
        String year = match.group(1);

        // Normalize variant
        String variant;
        if ("std".equalsIgnoreCase(variantOrSeqType) || "S".equalsIgnoreCase(variantOrSeqType)) {
            variant = "S";
        } else if ("alt".equalsIgnoreCase(variantOrSeqType) || "A".equalsIgnoreCase(variantOrSeqType)) {
            variant = "A";
        } else {
            variant = "S"; // Default to Standard
        }

        return classCode + "K_" + year + variant;
    }

    /**
     * Extract variant (S or A) from short_desc suffix.
     * Example: "SPK_26A" -> "A", "SPK_26S" -> "S"
     * Falls back to sequenceType if cannot extract.
     */
    private String extractVariantFromShortDesc(String shortDesc, String sequenceType) {
        if (shortDesc != null && shortDesc.length() > 0) {
            char lastChar = shortDesc.charAt(shortDesc.length() - 1);
            if (lastChar == 'S' || lastChar == 's') return "S";
            if (lastChar == 'A' || lastChar == 'a') return "A";
        }
        // Fallback to sequenceType
        return "alt".equalsIgnoreCase(sequenceType) ? "A" : "S";
    }

    /**
     * Check if folder exists on disk.
     * @param relativePath path relative to figures/
     * @return true if folder exists
     */
    public boolean folderExists(String relativePath) {
        File folder = new File(getFiguresPath() + "/" + relativePath);
        boolean exists = folder.exists() && folder.isDirectory();
        logger.trace("Checking folder {}: exists={}", relativePath, exists);
        return exists;
    }

    /**
     * Get the base path for figures.
     * @return figures path
     */
    public String getFiguresPath() {
        return FIGURES_PATH_BASE;
    }
}
