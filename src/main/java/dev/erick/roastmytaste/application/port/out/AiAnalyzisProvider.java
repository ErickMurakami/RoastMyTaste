package dev.erick.roastmytaste.application.port.out;

import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.model.RoastAnalysis;
import dev.erick.roastmytaste.domain.model.UserProfile;

public interface AiAnalyzisProvider {
    RoastAnalysis analyze(UserProfile profile, RoastLanguage language);
}
