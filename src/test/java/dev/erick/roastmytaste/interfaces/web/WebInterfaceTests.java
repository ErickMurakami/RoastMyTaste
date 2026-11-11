package dev.erick.roastmytaste.interfaces.web;

import dev.erick.roastmytaste.application.port.in.AnalyzeMusicTasteUseCase;
import dev.erick.roastmytaste.application.port.out.SpotifyProvider;
import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.exception.AiAnalysisException;
import dev.erick.roastmytaste.domain.exception.InsufficientDataException;
import dev.erick.roastmytaste.domain.exception.SpotifyProviderException;
import dev.erick.roastmytaste.domain.model.RoastAnalysis;
import dev.erick.roastmytaste.domain.model.Artist;
import dev.erick.roastmytaste.domain.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Client;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("local")
class WebInterfaceTests {

    private static final RoastAnalysis ANALYSIS = new RoastAnalysis(
            "Absolute Musical Shambles", "A concerning lack of taste.",
            "Your playlist is an algorithm having an identity crisis.",
            "Let someone else choose the next song.", 87, List.of("Musical Menace", "Certified Muppet"));
    private static final ClientRegistration SPOTIFY = ClientRegistration.withRegistrationId("spotify")
            .clientId("test-client").clientSecret("test-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://127.0.0.1:8080/login/oauth2/code/spotify")
            .authorizationUri("https://accounts.spotify.com/authorize")
            .tokenUri("https://accounts.spotify.com/api/token")
            .userInfoUri("https://api.spotify.com/v1/me").userNameAttributeName("sub")
            .scope("user-top-read").build();

    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @MockitoBean AnalyzeMusicTasteUseCase analyze;
    @MockitoBean SpotifyProvider spotify;

    @BeforeEach
    void configureMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private RequestPostProcessor listener() {
        return oauth2Login().clientRegistration(SPOTIFY).attributes(attributes -> {
            attributes.put("sub", "listener");
            attributes.put("display_name", "Alex Listener");
        });
    }

    private RequestPostProcessor spotifyClient() {
        return oauth2Client("spotify").clientRegistration(SPOTIFY).principalName("listener")
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                        "private-spotify-token", Instant.now(), Instant.now().plusSeconds(3600)));
    }

    @Test
    void landingIsPublicAndUsesExistingSpotifyLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk()).andExpect(view().name("index"))
                .andExpect(content().string(containsString("Your music")))
                .andExpect(content().string(containsString("hero-cta\" href=\"/oauth2/authorization/spotify\"")))
                .andExpect(content().string(containsString("https://github.com/Erickkamii/SpotifyRoaster")));
    }

    @Test
    void connectedLandingLinksToRoastRoom() throws Exception {
        mvc.perform(get("/").with(listener()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hero-cta\" href=\"/roast\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/css/app.css", "/js/roast.js", "/favicon.svg"})
    void assetsArePublic(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/roast", "/api/v1/analysis", "/api/v1/debug/profile", "/api/v1/me",
            "/api/v1/debug/top-tracks", "/api/v1/debug/top-artists"})
    void privatePagesAndApiRequireAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        verifyNoInteractions(analyze);
    }

    @Test
    void unauthenticatedGenerationNeverInvokesUseCase() throws Exception {
        mvc.perform(post("/roast").with(csrf())).andExpect(status().is3xxRedirection());
        verifyNoInteractions(analyze);
    }

    @Test
    void expiredAsyncSessionReturns401InsteadOfFollowingOAuthRedirects() throws Exception {
        mvc.perform(post("/roast").with(csrf()).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(analyze);
    }

    @Test
    void spotifyAuthorizationStartsWithExpectedScopeAndState() throws Exception {
        mvc.perform(get("/oauth2/authorization/spotify"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("https://accounts.spotify.com/authorize")))
                .andExpect(header().string("Location", containsString("scope=user-top-read")))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(header().string("Location", not(containsString("test-secret"))));
    }

    @Test
    void oauthFailureRedirectsToFriendlyLandingMessage() throws Exception {
        mvc.perform(get("/login/oauth2/code/spotify?error=access_denied&state=invalid"))
                .andExpect(redirectedUrl("/?loginError"));
        mvc.perform(get("/?loginError"))
                .andExpect(content().string(containsString("Spotify login did not finish")));
    }

    @Test
    void roastDefaultsToBritishEnglishAndDisplaysSessionProfileAndCsrf() throws Exception {
        mvc.perform(get("/roast").with(listener()))
                .andExpect(status().isOk()).andExpect(view().name("roast"))
                .andExpect(content().string(containsString("value=\"en-GB\" selected=\"selected\"")))
                .andExpect(model().attribute("languages", RoastLanguage.values()))
                .andExpect(content().string(containsString("Brazilian Portuguese")))
                .andExpect(content().string(containsString("Alex Listener")))
                .andExpect(content().string(containsString("action=\"/roast\" method=\"post\"><input type=\"hidden\" name=\"_csrf\"")))
                .andExpect(content().string(containsString("action=\"/logout\" method=\"post\"><input type=\"hidden\" name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("name=\"intensity\""))));
        verifyNoInteractions(analyze);
    }

    @Test
    void roastRoomIncludesIndependentCarouselsUsingExistingEndpoints() throws Exception {
        mvc.perform(get("/roast").with(listener()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-carousel=\"tracks\" data-source=\"/api/v1/debug/top-tracks\"")))
                .andExpect(content().string(containsString("data-carousel=\"artists\" data-source=\"/api/v1/debug/top-artists\"")))
                .andExpect(content().string(containsString("aria-roledescription=\"carousel\"")))
                .andExpect(content().string(containsString("aria-label=\"Next track\"")))
                .andExpect(content().string(containsString("aria-label=\"Previous artist\"")));
        verifyNoInteractions(spotify, analyze);
    }

    @Test
    void existingTrackEndpointSuppliesCarouselWithoutGeneratingRoast() throws Exception {
        when(spotify.getTopTracks("private-spotify-token")).thenReturn(List.of(
                new Track("track-id", "A Questionable Anthem", List.of("The Algorithms"), 180000, false,
                        "2024-01-01", "https://i.scdn.co/image/album-cover")));
        mvc.perform(get("/api/v1/debug/top-tracks").with(listener()).with(spotifyClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("track-id"))
                .andExpect(jsonPath("$[0].name").value("A Questionable Anthem"))
                .andExpect(jsonPath("$[0].artistsNames[0]").value("The Algorithms"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://i.scdn.co/image/album-cover"))
                .andExpect(content().string(not(containsString("private-spotify-token"))));
        verify(spotify).getTopTracks("private-spotify-token");
        verifyNoInteractions(analyze);
    }

    @Test
    void existingArtistEndpointSuppliesCarouselWithoutGeneratingRoast() throws Exception {
        when(spotify.getTopArtists("private-spotify-token")).thenReturn(List.of(
                new Artist("artist-id", "The Algorithms", List.of("indie rock"), "https://i.scdn.co/image/artist-photo")));
        mvc.perform(get("/api/v1/debug/top-artists").with(listener()).with(spotifyClient()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("artist-id"))
                .andExpect(jsonPath("$[0].name").value("The Algorithms"))
                .andExpect(jsonPath("$[0].genres[0]").value("indie rock"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://i.scdn.co/image/artist-photo"))
                .andExpect(content().string(not(containsString("private-spotify-token"))));
        verify(spotify).getTopArtists("private-spotify-token");
        verifyNoInteractions(analyze);
    }

    @Test
    void previewSupportsEmptyListeningHistory() throws Exception {
        when(spotify.getTopTracks("private-spotify-token")).thenReturn(List.of());
        mvc.perform(get("/api/v1/debug/top-tracks").with(listener()).with(spotifyClient()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verifyNoInteractions(analyze);
    }

    @Test
    void previewProviderFailuresKeepExistingApiErrorContract() throws Exception {
        when(spotify.getTopArtists("private-spotify-token")).thenThrow(new SpotifyProviderException("Spotify unavailable"));
        mvc.perform(get("/api/v1/debug/top-artists").with(listener()).with(spotifyClient()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SPOTIFY_PROVIDER_ERROR"));
        verifyNoInteractions(analyze);
    }

    @Test
    void profileNameIsOptionalAndEscaped() throws Exception {
        mvc.perform(get("/roast").with(oauth2Login().clientRegistration(SPOTIFY)))
                .andExpect(content().string(containsString("Music enthusiast")));
        mvc.perform(get("/roast").with(oauth2Login().clientRegistration(SPOTIFY)
                        .attributes(attributes -> attributes.put("display_name", "<script>profile</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;profile&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>profile</script>"))));
    }

    @Test
    void generationRequiresValidCsrf() throws Exception {
        mvc.perform(post("/roast").with(listener()).with(spotifyClient()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/roast").with(listener()).with(spotifyClient()).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(analyze);
    }

    @Test
    void logoutRequiresCsrfAndReturnsHome() throws Exception {
        mvc.perform(post("/logout").with(listener())).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(listener()).with(csrf())).andExpect(redirectedUrl("/"));
    }

    @Test
    void omittedLanguageUsesBritishEnglishAndRendersAllResultFields() throws Exception {
        when(analyze.analyze(anyString(), any())).thenReturn(ANALYSIS);
        mvc.perform(post("/roast").with(listener()).with(spotifyClient()).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("roast"))
                .andExpect(content().string(containsString(ANALYSIS.title())))
                .andExpect(content().string(containsString(ANALYSIS.musicalDiagnosis())))
                .andExpect(content().string(containsString(ANALYSIS.roast())))
                .andExpect(content().string(containsString(ANALYSIS.recommendation())))
                .andExpect(content().string(containsString("Musical Menace")))
                .andExpect(content().string(containsString("<meter min=\"0\" max=\"100\" value=\"87\"")))
                .andExpect(content().string(containsString("Roast Again")))
                .andExpect(content().string(not(containsString("private-spotify-token"))));
        verify(analyze).analyze("private-spotify-token", RoastLanguage.ENGLISH_UK);
    }

    @ParameterizedTest
    @EnumSource(RoastLanguage.class)
    void allSupportedLanguagesUseExistingParserAndStaySelected(RoastLanguage language) throws Exception {
        when(analyze.analyze(anyString(), any())).thenReturn(ANALYSIS);
        mvc.perform(post("/roast").param("language", language.code()).with(listener()).with(spotifyClient()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + language.code() + "\" selected=\"selected\"")))
                .andExpect(content().string(containsString("tabindex=\"-1\" lang=\"" + language.code() + "\"")));
        verify(analyze).analyze("private-spotify-token", language);
    }

    @Test
    void languageCodesAreCaseInsensitive() throws Exception {
        when(analyze.analyze(anyString(), any())).thenReturn(ANALYSIS);
        mvc.perform(post("/roast").param("language", "PT-br").with(listener()).with(spotifyClient()).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedLanguage", "pt-BR"));
        verify(analyze).analyze("private-spotify-token", RoastLanguage.PORTUGUESE);
    }

    @Test
    void unsupportedLanguageIsFriendlyAndNeverCallsProviders() throws Exception {
        mvc.perform(post("/roast").param("language", "<script>bad</script>")
                        .with(listener()).with(spotifyClient()).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(view().name("roast"))
                .andExpect(content().string(containsString("not on the setlist")))
                .andExpect(content().string(not(containsString("<script>bad</script>"))));
        verifyNoInteractions(analyze);
    }

    @Test
    void everyAiFieldIsEscapedAndLongTextIsPreserved() throws Exception {
        String unsafe = "<script>alert('x')</script>";
        String longRoast = unsafe + " Your playlist needs help.".repeat(250);
        when(analyze.analyze(anyString(), any())).thenReturn(
                new RoastAnalysis(unsafe, unsafe, longRoast, unsafe, 100, List.of(unsafe)));
        mvc.perform(post("/roast").with(listener()).with(spotifyClient()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(unsafe))))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(containsString(" Your playlist needs help.".repeat(250))))
                .andExpect(content().string(containsString("<meter min=\"0\" max=\"100\" value=\"100\"")));
    }

    @Test
    void insufficientListeningDataHasFriendlyRetryMessage() throws Exception {
        assertFailure(new InsufficientDataException("private details"), 422, "Not enough evidence", false);
    }

    @Test
    void spotifyOutageHasFriendlyRetryMessage() throws Exception {
        assertFailure(new SpotifyProviderException("private details"), 502, "Spotify is taking an intermission", false);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void spotifyAuthFailuresOfferReconnect(int status) throws Exception {
        assertFailure(new SpotifyProviderException("private details", new HttpClientErrorException(HttpStatus.valueOf(status))),
                401, "Reconnect", true);
    }

    @Test
    void expiredOAuthClientOffersReconnect() throws Exception {
        assertFailure(new OAuth2AuthorizationException(new OAuth2Error("invalid_grant")), 401, "expired", true);
    }

    @Test
    void aiTimeoutOrMalformedOutputHasFriendlyRetryMessage() throws Exception {
        assertFailure(new AiAnalysisException("private details", new IllegalArgumentException("secret key")),
                502, "Our critic lost the plot", false);
    }

    @Test
    void emptyAiResponseHasFriendlyRetryMessage() throws Exception {
        when(analyze.analyze(anyString(), any())).thenReturn(null);
        mvc.perform(post("/roast").with(listener()).with(spotifyClient()).with(csrf()))
                .andExpect(status().isBadGateway()).andExpect(view().name("roast"));
    }

    @Test
    void unexpectedFailuresNeverRevealDetails() throws Exception {
        assertFailure(new IllegalStateException("private details"), 500, "wrong note", false);
    }

    private void assertFailure(RuntimeException failure, int status, String message, boolean reconnect) throws Exception {
        when(analyze.analyze(anyString(), any())).thenThrow(failure);
        mvc.perform(post("/roast").param("language", "es").with(listener()).with(spotifyClient()).with(csrf()))
                .andExpect(status().is(status)).andExpect(view().name("roast"))
                .andExpect(model().attribute("selectedLanguage", "es"))
                .andExpect(model().attribute("reconnect", reconnect))
                .andExpect(content().string(containsString(message)))
                .andExpect(content().string(not(containsString("private details"))))
                .andExpect(content().string(not(containsString("secret key"))))
                .andExpect(content().string(containsString("Roast My Taste")));
    }

    @ParameterizedTest
    @EnumSource(RoastLanguage.class)
    void existingRestAnalysisKeepsJsonContractForAllLanguages(RoastLanguage language) throws Exception {
        when(analyze.analyze(anyString(), any())).thenReturn(ANALYSIS);
        mvc.perform(get("/api/v1/analysis").param("language", language.code()).with(listener()).with(spotifyClient()))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.title").value(ANALYSIS.title()))
                .andExpect(jsonPath("$.musicalDiagnosis").value(ANALYSIS.musicalDiagnosis()))
                .andExpect(jsonPath("$.roast").value(ANALYSIS.roast()))
                .andExpect(jsonPath("$.recommendation").value(ANALYSIS.recommendation()))
                .andExpect(jsonPath("$.chaosScore").value(87))
                .andExpect(jsonPath("$.personalityTags[0]").value("Musical Menace"))
                .andExpect(content().string(not(containsString("private-spotify-token"))));
        verify(analyze).analyze("private-spotify-token", language);
    }

    @Test
    void existingApiStillDefaultsToBritishEnglish() throws Exception {
        when(analyze.analyze(anyString(), any())).thenReturn(ANALYSIS);
        mvc.perform(get("/api/v1/analysis").with(listener()).with(spotifyClient())).andExpect(status().isOk());
        verify(analyze).analyze("private-spotify-token", RoastLanguage.ENGLISH_UK);
    }

    @Test
    void existingApiErrorsRemainJson() throws Exception {
        when(analyze.analyze(anyString(), any())).thenThrow(new AiAnalysisException("private details", null));
        mvc.perform(get("/api/v1/analysis").with(listener()).with(spotifyClient()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AI_ANALYSIS_ERROR"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().string(not(containsString("private details"))));
    }
}
