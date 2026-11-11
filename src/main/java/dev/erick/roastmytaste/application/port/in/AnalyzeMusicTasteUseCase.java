package dev.erick.roastmytaste.application.port.in;

import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.model.RoastAnalysis;

public interface AnalyzeMusicTasteUseCase {
    RoastAnalysis analyze(String accessToken, RoastLanguage language);
}
