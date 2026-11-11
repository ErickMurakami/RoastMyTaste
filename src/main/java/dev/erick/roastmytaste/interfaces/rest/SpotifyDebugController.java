package dev.erick.roastmytaste.interfaces.rest;

import dev.erick.roastmytaste.application.port.out.SpotifyProvider;
import dev.erick.roastmytaste.domain.model.Artist;
import dev.erick.roastmytaste.domain.model.Track;
import dev.erick.roastmytaste.domain.model.UserProfile;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/debug")
public class SpotifyDebugController {

    private final SpotifyProvider spotifyProvider;

    public SpotifyDebugController(SpotifyProvider spotifyProvider) {
        this.spotifyProvider = spotifyProvider;
    }

    @GetMapping("/top-tracks")
    public List<Track> topTracks(
            @RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient client) {

        return spotifyProvider.getTopTracks(
                client.getAccessToken().getTokenValue()
        );
    }

    @GetMapping("/top-artists")
    public List<Artist> topArtists(
            @RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient client) {

        return spotifyProvider.getTopArtists(
                client.getAccessToken().getTokenValue()
        );
    }

    @GetMapping("/profile")
    public UserProfile profile(
            @RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient client) {

        var token = client.getAccessToken().getTokenValue();

        return UserProfile.from(
                spotifyProvider.getTopTracks(token),
                spotifyProvider.getTopArtists(token)
        );
    }
}
