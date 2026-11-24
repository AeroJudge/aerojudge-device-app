package com.aerojudge.judge.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.Arrays;

import com.aerojudge.judge.service.SettingService;
import com.aerojudge.judge.dto.SettingDTO;

@Profile("!test")
@Component
public class CommandLineAppStartupRunner implements ApplicationRunner {
    private static final Logger logger =
            LoggerFactory.getLogger(CommandLineAppStartupRunner.class);

    @Override
    public void run(ApplicationArguments args) throws Exception {

        logger.debug("Application started with command-line arguments: {}", Arrays.toString(args.getSourceArgs()));
        logger.debug("NonOptionArgs: {}", args.getNonOptionArgs());
        logger.debug("OptionNames: {}", args.getOptionNames());

        for (String name : args.getOptionNames()){
            logger.debug("arg: " + name + "=" + args.getOptionValues(name));
        }

        logger.info("Config Path: " + SettingUtils.getApplicationConfigPath());

        SettingService settingSvc = new SettingService();
        SettingDTO settingDTO = settingSvc.getSettings();

        logger.info ("Judge ID: " + settingDTO.getJudge_id());
        logger.info ("Line No.: " + settingDTO.getLine_number());
        logger.info ("Score Host: " + settingDTO.getScore_host());
        logger.info ("Score Port: " + settingDTO.getScore_http_port());
        logger.info ("Score Timeout: " + settingDTO.getScore_timeout());
        logger.info ("Score Poll Timeout: " + settingDTO.getScore_poll_timeout());
        logger.info ("Language: " + settingDTO.getLanguage());
    }
}