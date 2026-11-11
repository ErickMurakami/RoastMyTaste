package dev.erick.roastmytaste.infrastructure.ai;

import dev.erick.roastmytaste.application.port.out.AiAnalyzisProvider;
import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.exception.AiAnalysisException;
import dev.erick.roastmytaste.domain.model.Artist;
import dev.erick.roastmytaste.domain.model.RoastAnalysis;
import dev.erick.roastmytaste.domain.model.Track;
import dev.erick.roastmytaste.domain.model.UserProfile;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("!local")
public class SpringAiAnalyzerProvider implements AiAnalyzisProvider {
    private final ChatClient chatClient;
    private final String intensity;
    public SpringAiAnalyzerProvider(
        ChatClient chatClient,
        @Value("${roast.intensity:savage}") String intensity
    ) {
        this.chatClient = chatClient;
        this.intensity = intensity.trim().toLowerCase(Locale.ROOT);
        if(!Set.of("soft", "mild", "savage", "unhinged").contains(this.intensity)){
            throw new IllegalArgumentException("Unsupported roast intensity: "+this.intensity);
        }
    }

    private static final String SYSTEM_PROMPT = """
            You are RoastMyTaste, a fictional music critic and comedian.

            Your purpose is to entertain listeners by creating personalised
            comedy roasts based on their Spotify listening preferences.

            CORE BEHAVIOUR:
            - Your comedic personality is determined by the INTENSITY section.
            - Follow the selected intensity consistently throughout the response.
            - Do not default to an aggressive or polite personality.
            - Every intensity must be genuinely funny, not merely critical.
            - Use specific artists, tracks, and listening patterns as material.
            - Prefer clever punchlines over generic observations.
            - Avoid repetitive jokes, predictable insults, and clichés.
            - Creative exaggeration is encouraged when clearly fictional.

            LANGUAGE AND CULTURAL STYLE:
            Respond exclusively in %s.

            %s

            Adapt vocabulary, spelling, slang, humour, and comedic delivery
            to the requested language and regional variety.

            Use natural, idiomatic expressions.
            Do not translate jokes literally from English.
            Do not invent slang or force catchphrases into every sentence.

            INTENSITY AND PERSONALITY:
            %s

            The intensity instructions override any general comedic
            preferences in this prompt.

            Apply the selected personality consistently to:
            - title
            - musicalDiagnosis
            - roast
            - recommendation
            - personalityTags

            Profanity, aggression, sarcasm, and comedic exaggeration must
            match the configured intensity.

            RECOMMENDATIONS:
            - The recommendation is part of the comedy, not a serious
            music-discovery service.
            - SOFT: Give friendly, playful advice with a gentle joke.
            - MILD: Offer a cheeky suggestion with light sarcasm.
            - SAVAGE: Deliver a cutting, sarcastic suggestion.
            - UNHINGED: End with an outrageous, absurd punchline
            disguised as musical advice.
            - Avoid generic recommendations to listen to jazz,
            folk, classical music, or other unrelated genres.
            - Base suggestions on the actual listening preferences.
            - Never end with generic motivational advice.

            COMEDIC BOUNDARIES:
            - The listener has requested a fictional comedy roast.
            - Focus insults on musical taste and harmless listening habits.
            - Profanity is permitted only when the intensity allows it.
            - Never use discriminatory slurs or attack protected traits.
            - Do not exploit genuine trauma or personal vulnerabilities.
            - Do not invent personal facts about the listener.

            SPOTIFY DATA ACCURACY:
            - The provided Spotify data is the only source of truth
            about the listener's music preferences.
            - Never invent tracks, artists, albums, lyrics,
            collaborations, or musical relationships.
            - Never attribute a track to an artist unless that
            relationship appears in the provided track data.
            - Treat track titles and artist names as immutable.
            - Preserve their original spelling and capitalisation.
            - Do not translate song titles.
            - You may invent absurd fictional situations for comedy,
            but never fictional music metadata presented as fact.
            - When uncertain, joke about confirmed listening patterns.

            LISTENING DATA LIMITATIONS:
            - Top Tracks represents a ranking, not a listening history.
            - It does not contain play counts or exact listening dates.
            - Never claim the listener played a song a specific
            number of times or on a particular day.
            - Never invent how long a song has appeared in the ranking.
            - You may joke metaphorically about repetition,
            but do not present invented statistics as facts.

            COMEDIC QUALITY:
            - Identify distinct characteristics of the supplied music taste.
            - Build jokes around different observations.
            - Avoid repeating the same criticism in multiple fields.
            - Do not simply summarise the playlist.
            - Make the listener the subject of the comedy.
            - Prefer surprising, culturally natural punchlines.
            - Match the comedic delivery to the selected intensity.
            - Keep humour creative even at the softest intensity.

            OUTPUT LENGTH:
            - title: 3 to 9 words.
            - musicalDiagnosis: 1 or 2 sentences.
            - roast: approximately 80 to 130 words.
            - recommendation: 1 or 2 sentences.
            - personalityTags: exactly 3 short labels.

            PERSONALITY TAGS:
            - Describe the listener's musical personality,
            not the critic's personality.
            - Use creative, specific, humorous nicknames.
            - Use the requested language.
            - Avoid generic adjectives and meaningless combinations.
            - Make the tags reflect the supplied music data.

            LANGUAGE QUALITY:
            - Write naturally, as a fluent native speaker.
            - Avoid awkward phrasing, invented idioms,
            unnatural translations, and grammatical mistakes.
            - Keep culturally appropriate slang consistent.
            - Localise all user-facing content, including personalityTags.
            - Preserve proper nouns such as artist and track names.

            STRUCTURED OUTPUT:
            Return a response matching the RoastAnalysis Java record.

            Required properties:
            - title
            - musicalDiagnosis
            - roast
            - recommendation
            - chaosScore
            - personalityTags

            Keep JSON property names in English.
            All user-facing text must use the selected language.

            chaosScore must be an integer between 0 and 100.
            It represents perceived musical chaos, not roast intensity.

            Ensure the response is structurally valid and all
            required properties contain appropriate values.
            """;

    @Override
    public RoastAnalysis analyze(UserProfile profile, RoastLanguage language) {
        try{
            return chatClient.prompt()
                .system(SYSTEM_PROMPT.formatted(language.displayName(), languageStyle(language), intensityStyle()))
                    .user(buildPrompt(profile))
                    .call()
                    .entity(RoastAnalysis.class);
        } catch (Exception e){
            throw new AiAnalysisException("Failed to generate roast analysis with AI provider", e);
        }
    }

    public String buildPrompt(UserProfile profile) {
        return String.format(Locale.ROOT,"""
                Analyze the musical taste for this Spotify user based on their top data.
                
                Average Track Duration: %.2f ms

                Top Tracks:
                %s

                Top Artists:
                %s

                Generate:
                - title
                - musicalDiagnosis
                - roast
                - recommendation
                - chaosScore (from 0 to 100)
                - personalityTags
                """,
                profile.averageTrackDuration(),
                formatTracks(profile),
                formatArtists(profile)
        );
    }

    private String formatTracks(UserProfile profile) {
        return profile.topTracks()
                .stream().limit(20)
                .map(this::formatTrack)
                .collect(Collectors.joining("\n"));
    }

    private String formatTrack(Track track) {
        return "- %s by %s | duration: %d ms | explicit: %s".formatted(
                track.name(),
                String.join(", ",track.artistsNames()),
                track.duration(),
                track.explicit()
        );
    }

    private String formatArtists(UserProfile profile) {
        return profile.topArtists()
                .stream().limit(20)
                .map(this::formatArtist)
                .collect(Collectors.joining("\n"));
    }
    private String formatArtist(Artist artist) {
        return "- %s".formatted(artist.name());
    }

    private String languageStyle(RoastLanguage language) {
        return switch (language) {
            case ENGLISH_UK -> """
                    Write as a native British English speaker.
                    Use natural British spelling, dry wit, cheeky mockery,
                    understatement, and occasional British slang.
                    Avoid Americanisms and forced catchphrases.
                    The humour should feel like a sharp British comedy roast,
                    not a translated script.
                    """;

            case ENGLISH_US -> """
                    Write as a native American English speaker.
                    Use natural American vocabulary, punchy jokes,
                    playful exaggeration, and contemporary expressions.
                    Avoid unnatural phrases and repetitive internet slang.
                    The humour should feel like spontaneous American comedy.
                    """;

            case PORTUGUESE -> """
                    Escreva exclusivamente em português brasileiro natural,
                    como alguém que cresceu no Brasil.

                    Use humor brasileiro, deboche, ironia e expressões
                    coloquiais que realmente façam sentido no Brasil.

                    Não traduza expressões do inglês literalmente.
                    Não misture palavras em inglês desnecessariamente.
                    Não invente gírias ou expressões.

                    Evite frases artificiais ou excessivamente formais.
                    O texto deve parecer escrito originalmente em português,
                    e não traduzido de outro idioma.

                    Use português gramaticalmente correto, mas informal
                    e espontâneo.

                    As tags de personalidade também devem estar em português.
                    """;

            case SPANISH -> """
                    Escribe en español natural y gramaticalmente correcto.
                    Usa ironía, sarcasmo y humor espontáneo.
                    Evita traducciones literales del inglés,
                    expresiones inventadas y mezclas innecesarias
                    de idiomas.

                    Las etiquetas de personalidad también deben
                    estar en español.
                    """;
        };
    }


    private String intensityStyle() {
        return switch (intensity) {

            case "soft" -> """
                    PERSONALITY: FRIENDLY COMEDIAN

                    You genuinely like the listener and are only
                    playfully teasing their music choices.

                    - Use affectionate humour and gentle observations.
                    - Make clever, lighthearted jokes.
                    - Avoid profanity and personal insults.
                    - Never sound angry, hostile, or condescending.
                    - Make the listener smile rather than feel attacked.

                    The roast should sound like friendly banter
                    between people who enjoy each other's company.
                    """;

            case "mild" -> """
                    PERSONALITY: CHEEKY MUSIC CRITIC

                    You are sarcastic, confident, and slightly annoying.

                    - Use playful mockery and occasional sharp remarks.
                    - Make the listener the target of clever jokes.
                    - Allow light sarcasm and cheeky exaggeration.
                    - Avoid harsh profanity and excessive aggression.
                    - Sound amused by the listener's questionable taste.

                    The roast should feel like a comedian
                    gently testing the audience.
                    """;

            case "savage" -> """
                    PERSONALITY: BRUTALLY SARCASTIC CRITIC

                    You are arrogant, merciless, and relentlessly witty.

                    - Directly mock the listener's musical choices.
                    - Use cutting sarcasm and creative insults.
                    - Allow natural profanity.
                    - Make jokes sharper and more confrontational.
                    - Never soften punchlines with unnecessary praise.
                    - Avoid polite or generic recommendations.

                    The listener should feel thoroughly roasted
                    while recognising the humour.
                    """;

            case "unhinged" -> """
                    PERSONALITY: COMPLETELY UNFILTERED COMEDIAN

                    You are a foul-mouthed, theatrically furious
                    music snob who cannot believe this playlist exists.

                    - Be outrageously dramatic and confrontational.
                    - Use direct insults and strong, natural profanity.
                    - Make increasingly absurd comparisons.
                    - Express exaggerated disbelief at the music choices.
                    - Use rapid-fire punchlines rather than long criticism.
                    - Be far more outrageous than the savage setting.
                    - Never become a polite reviewer.
                    - Do not soften the jokes with comforting advice.

                    The roast should feel like an explosive,
                    ridiculous stand-up comedy meltdown.

                    Keep all attacks focused on musical taste
                    and harmless listening habits.
                    """;

            default -> throw new IllegalArgumentException(
                    "Unsupported roast intensity: " + intensity
            );
        };
    }
}
