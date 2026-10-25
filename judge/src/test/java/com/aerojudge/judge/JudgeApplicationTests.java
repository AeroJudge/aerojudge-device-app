package com.aerojudge.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import com.aerojudge.judge.utils.SettingUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@ContextConfiguration(initializers = JudgeApplicationTests.IsolatedData.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JudgeApplicationTests {

	@TempDir static Path data;

	static class IsolatedData implements ApplicationContextInitializer<ConfigurableApplicationContext> {
		@Override public void initialize(ConfigurableApplicationContext context) {
			SettingUtils.setApplicationConfigPath(data.toString());
		}
	}

	@Test
	void contextLoads() {
	}

}
