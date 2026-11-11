package dev.erick.roastmytaste.application.service;

import dev.erick.roastmytaste.application.port.in.AnalyzeMusicTasteUseCase;
import dev.erick.roastmytaste.application.port.out.AiAnalyzisProvider;
import dev.erick.roastmytaste.application.port.out.SpotifyProvider;
import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.exception.InsufficientDataException;
import dev.erick.roastmytaste.domain.model.RoastAnalysis;
import dev.erick.roastmytaste.domain.model.UserProfile;
import org.springframework.stereotype.Service;

@Service
public class AnalyzeMusicTasteService implements AnalyzeMusicTasteUseCase {
    private final SpotifyProvider spotifyProvider;
    private final AiAnalyzisProvider aiAnalyzisProvider;

    public AnalyzeMusicTasteService(SpotifyProvider spotifyProvider, AiAnalyzisProvider aiAnalyzisProvider) {
        this.spotifyProvider = spotifyProvider;
        this.aiAnalyzisProvider = aiAnalyzisProvider;
    }

    @Override
    public RoastAnalysis analyze(String accessToken, RoastLanguage language) {
        var topTracks = spotifyProvider.getTopTracks(accessToken);
        var topArtists = spotifyProvider.getTopArtists(accessToken);

        if (topArtists.isEmpty()||topTracks.isEmpty()) {
            throw new InsufficientDataException("Spotify didn't return enough data to generate an analyse");
        }
        var profile = UserProfile.from(topTracks, topArtists);
        return aiAnalyzisProvider.analyze(profile, language);
    }
}
