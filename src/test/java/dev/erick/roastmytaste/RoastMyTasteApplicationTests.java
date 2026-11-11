package dev.erick.roastmytaste;

import dev.erick.roastmytaste.application.port.out.AiAnalyzisProvider;
import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.model.UserProfile;
import dev.erick.roastmytaste.infrastructure.ai.FakeAiAnalyzerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RoastMyTasteApplication.class)
@ActiveProfiles("local")
class RoastMyTasteApplicationTests {

	@Autowired
	AiAnalyzisProvider analyzer;

	@Test
	void contextLoads() {
		assertThat(analyzer).isInstanceOf(FakeAiAnalyzerProvider.class);
	}

	@ParameterizedTest
	@EnumSource(RoastLanguage.class)
	void fakeProviderIsDeterministicInEveryLanguage(RoastLanguage language) {
		var profile = UserProfile.from(List.of(), List.of());
		var result = analyzer.analyze(profile, language);
		assertThat(result).isEqualTo(analyzer.analyze(profile, language));
		assertThat(result.title()).isNotBlank();
		assertThat(result.chaosScore()).isBetween(0, 100);
	}
}
