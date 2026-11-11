package dev.erick.roastmytaste.infrastructure.spotify.dto;

import java.util.List;
import java.util.Objects;

public record SpotifyImageResponse(String url, Integer height, Integer width) {

    public static String firstUrl(List<SpotifyImageResponse> images) {
        if (images == null) {
            return null;
        }
        return images.stream()
                .filter(Objects::nonNull)
                .map(SpotifyImageResponse::url)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }
}
