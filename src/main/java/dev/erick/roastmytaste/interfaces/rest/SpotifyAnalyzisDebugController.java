package dev.erick.roastmytaste.interfaces.rest;

import dev.erick.roastmytaste.application.port.in.AnalyzeMusicTasteUseCase;
import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.model.RoastAnalysis;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpotifyAnalyzisDebugController {

    private final AnalyzeMusicTasteUseCase analyzeMusicTasteUseCase;

    public SpotifyAnalyzisDebugController(AnalyzeMusicTasteUseCase analyzeMusicTasteUseCase) {
        this.analyzeMusicTasteUseCase = analyzeMusicTasteUseCase;
    }

    @GetMapping("/api/v1/analysis")
    public RoastAnalysis analyze(
            @RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient client,
            @RequestParam(defaultValue = "en-GB") String language    
        ) {
        RoastLanguage roastLanguage = RoastLanguage.fromCode(language);
        return analyzeMusicTasteUseCase.analyze(
                client.getAccessToken().getTokenValue(),
                roastLanguage
        );
    }
}
