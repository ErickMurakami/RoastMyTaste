package dev.erick.roastmytaste.infrastructure.spotify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SpotifyArtworkTests {

    private MockRestServiceServer server;
    private SpotifyProviderAdapter spotify;

    @BeforeEach
    void configureClient() {
        var builder = RestClient.builder().baseUrl("https://api.spotify.com/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        var client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
                .build().createClient(SpotifyApiClient.class);
        spotify = new SpotifyProviderAdapter(client);
    }

    @Test
    void albumArtworkFlowsThroughTheExistingTrackRequest() {
        expectTracks("""
                {"id":"album-id","release_date":"2025-01-01","images":[
                    {"url":"https://i.scdn.co/image/album-cover","width":640,"height":640},
                    {"url":"https://i.scdn.co/image/album-small","width":64,"height":64}
                ]}
                """);

        var track = spotify.getTopTracks("test-token").getFirst();

        assertThat(track.imageUrl()).isEqualTo("https://i.scdn.co/image/album-cover");
        assertThat(track.name()).isEqualTo("A Questionable Anthem");
        assertThat(track.artistsNames()).containsExactly("The Algorithms");
        assertThat(track.releaseDate()).isEqualTo("2025-01-01");
        server.verify();
    }

    @Test
    void artistArtworkFlowsThroughTheExistingArtistRequest() {
        expectArtists("[{\"url\":\"https://i.scdn.co/image/artist-photo\",\"width\":640,\"height\":480}]");

        var artist = spotify.getTopArtists("test-token").getFirst();

        assertThat(artist.imageUrl()).isEqualTo("https://i.scdn.co/image/artist-photo");
        assertThat(artist.name()).isEqualTo("The Algorithms");
        assertThat(artist.genres()).containsExactly("indie rock");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"images\":null}", "{\"images\":[]}"})
    void tracksWithoutArtworkStillLoad(String album) {
        expectTracks(album);

        assertThat(spotify.getTopTracks("test-token").getFirst().imageUrl()).isNull();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "[null,{\"url\":null},{\"url\":\"\"}]"})
    void artistsWithoutArtworkStillLoad(String images) {
        expectArtists(images);

        assertThat(spotify.getTopArtists("test-token").getFirst().imageUrl()).isNull();
        server.verify();
    }

    @Test
    void missingImageEntriesDoNotHideAnAvailableImage() {
        expectArtists("[null,{\"url\":\"\"},{\"url\":\"https://i.scdn.co/image/available\"}]");

        assertThat(spotify.getTopArtists("test-token").getFirst().imageUrl())
                .isEqualTo("https://i.scdn.co/image/available");
        server.verify();
    }

    private void expectTracks(String album) {
        server.expect(requestTo("https://api.spotify.com/v1/me/top/tracks?time_range=long_term&limit=20"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"track-id","name":"A Questionable Anthem",
                        "artists":[{"id":"artist-id","name":"The Algorithms"}],
                        "duration_ms":180000,"explicit":false,"album":%s}],"total":1,"limit":20,"offset":0}
                        """.formatted(album), MediaType.APPLICATION_JSON));
    }

    private void expectArtists(String images) {
        server.expect(requestTo("https://api.spotify.com/v1/me/top/artists?time_range=long_term&limit=20"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"artist-id","name":"The Algorithms","genres":["indie rock"],
                        "images":%s}],"total":1,"limit":20,"offset":0}
                        """.formatted(images), MediaType.APPLICATION_JSON));
    }
}
