package dev.erick.roastmytaste.infrastructure.ai;

import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.exception.AiAnalysisException;
import dev.erick.roastmytaste.domain.model.RoastAnalysis;
import dev.erick.roastmytaste.domain.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiAnalyzerProviderTests {

    private final ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final UserProfile profile = UserProfile.from(List.of(), List.of());

    @Test
    void returnsTheExistingStructuredResponse() {
        var expected = new RoastAnalysis("Title", "Diagnosis", "Roast", "Advice", 42, List.of("Tag"));
        when(client.prompt().system(anyString()).user(anyString()).call().entity(RoastAnalysis.class))
                .thenReturn(expected);

        assertThat(new SpringAiAnalyzerProvider(client, "mild").analyze(profile, RoastLanguage.ENGLISH_UK))
                .isEqualTo(expected);
    }

    @Test
    void wrapsMalformedStructuredOutputAsDomainFailure() {
        var malformed = new IllegalArgumentException("Invalid structured response");
        when(client.prompt().system(anyString()).user(anyString()).call().entity(RoastAnalysis.class))
                .thenThrow(malformed);

        assertThatThrownBy(() -> new SpringAiAnalyzerProvider(client, "savage")
                .analyze(profile, RoastLanguage.PORTUGUESE))
                .isInstanceOf(AiAnalysisException.class).hasCause(malformed);
    }

    @Test
    void wrapsProviderTimeoutAsDomainFailure() {
        var timeout = new RuntimeException("Provider timed out");
        when(client.prompt().system(anyString()).user(anyString()).call()).thenThrow(timeout);

        assertThatThrownBy(() -> new SpringAiAnalyzerProvider(client, "unhinged")
                .analyze(profile, RoastLanguage.SPANISH))
                .isInstanceOf(AiAnalysisException.class).hasCause(timeout);
    }
}
