package dev.erick.roastmytaste.interfaces.rest;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
public class AuthDebugController {

    private final RestClient spotifyRestClient =
            RestClient.create("https://api.spotify.com/v1");

    @GetMapping("/api/v1/me")
    public Map<String, Object> me(
            @RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient client) {

        @SuppressWarnings("unchecked")
        Map<String, Object> spotifyUser = spotifyRestClient.get()
                .uri("/me")
                .headers(headers -> headers.setBearerAuth(
                        client.getAccessToken().getTokenValue()
                ))
                .retrieve()
                .body(Map.class);

        if (spotifyUser == null) {
            throw new IllegalStateException("Spotify returned an empty profile");
        }

        return Map.of(
                "authenticated", true,
                "name", String.valueOf(spotifyUser.getOrDefault("id", "")),
                "authorities", Map.of("attributes", spotifyUser)
        );
    }
}
