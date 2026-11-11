package dev.erick.roastmytaste.infrastructure.spotify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.erick.roastmytaste.domain.model.Track;

import java.util.List;

public record SpotifyTrackResponse(
        String id,
        String name,
        List<SpotifyArtistSummaryResponse> artists,
        @JsonProperty("duration_ms")
        Integer durationMs,
        SpotifyAlbumSummaryResponse album,
        Boolean explicit
) {
    public Track toDomain(){
        var artistsName = artists == null
                ? List.<String>of()
                : artists.stream()
                    .map(SpotifyArtistSummaryResponse::name)
                    .toList();

        var releaseDate = (album != null && album.releaseDate() != null)
                ? album.releaseDate()
                : "Unknown";

        return new Track(
                id,
                name,
                artistsName,
                durationMs,
                explicit,
                releaseDate,
                album == null ? null : SpotifyImageResponse.firstUrl(album.images())
        );
    }

    public record SpotifyArtistSummaryResponse(
            String id,
            String name
    ){
    }

    public record SpotifyAlbumSummaryResponse(
            String id,
            @JsonProperty("release_date")
            String releaseDate,
            List<SpotifyImageResponse> images
    ){}
}
