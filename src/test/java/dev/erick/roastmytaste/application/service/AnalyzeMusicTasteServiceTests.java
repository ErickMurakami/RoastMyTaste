package dev.erick.roastmytaste.application.service;

import dev.erick.roastmytaste.application.port.out.AiAnalyzisProvider;
import dev.erick.roastmytaste.application.port.out.SpotifyProvider;
import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.exception.InsufficientDataException;
import dev.erick.roastmytaste.domain.model.Artist;
import dev.erick.roastmytaste.domain.model.Track;
import dev.erick.roastmytaste.domain.model.UserProfile;
import dev.erick.roastmytaste.infrastructure.ai.FakeAiAnalyzerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyzeMusicTasteServiceTests {

    @Mock SpotifyProvider spotify;
    @Mock AiAnalyzisProvider ai;

    @Test
    void orchestratesExistingProvidersAndPassesLanguage() {
        var tracks = List.of(new Track("track-id", "Song", List.of("Artist"), 180000, false, "2024-01-01"));
        var artists = List.of(new Artist("artist-id", "Artist", List.of("rock")));
        when(spotify.getTopTracks("token")).thenReturn(tracks);
        when(spotify.getTopArtists("token")).thenReturn(artists);
        var profile = UserProfile.from(tracks, artists);
        var result = new FakeAiAnalyzerProvider().analyze(profile, RoastLanguage.SPANISH);
        when(ai.analyze(profile, RoastLanguage.SPANISH)).thenReturn(result);

        assertThat(new AnalyzeMusicTasteService(spotify, ai).analyze("token", RoastLanguage.SPANISH)).isEqualTo(result);
        verify(spotify).getTopTracks("token");
        verify(spotify).getTopArtists("token");
        verify(ai).analyze(profile, RoastLanguage.SPANISH);
        verifyNoMoreInteractions(spotify, ai);
    }

    @Test
    void insufficientDataNeverCallsAi() {
        when(spotify.getTopTracks("token")).thenReturn(List.of());
        when(spotify.getTopArtists("token")).thenReturn(List.of());

        assertThatThrownBy(() -> new AnalyzeMusicTasteService(spotify, ai).analyze("token", RoastLanguage.ENGLISH_UK))
                .isInstanceOf(InsufficientDataException.class);
        verifyNoInteractions(ai);
    }
}
