package dev.erick.roastmytaste.infrastructure.spotify.dto;

import dev.erick.roastmytaste.domain.model.Artist;

import java.util.List;

public record SpotifyArtistsResponse(
        String id,
        String name,
        List<String> genres,
        List<SpotifyImageResponse> images
) {
    public Artist toDomain(){
        return new Artist(id, name, genres, SpotifyImageResponse.firstUrl(images));
    }
}
