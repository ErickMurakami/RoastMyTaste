package dev.erick.roastmytaste.domain.model;

import java.util.List;
import java.util.Objects;

public record Track(
        String id,
        String name,
        List<String> artistsNames,
        int duration,
        boolean explicit,
        String releaseDate,
        String imageUrl
) {
    public Track(String id, String name, List<String> artistsNames, int duration, boolean explicit, String releaseDate) {
        this(id, name, artistsNames, duration, explicit, releaseDate, null);
    }

    public Track{
        Objects.requireNonNull(id, "Track ID is required");
        Objects.requireNonNull(name, "Track name is required");

        artistsNames = artistsNames == null ? List.of() : List.copyOf(artistsNames);

        if (duration < 0) {
            throw new IllegalArgumentException("Duration must not be negative");
        }
    }
}
