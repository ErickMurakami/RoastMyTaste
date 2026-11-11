
package dev.erick.roastmytaste.infrastructure.ai;

import dev.erick.roastmytaste.application.port.out.AiAnalyzisProvider;
import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.model.RoastAnalysis;
import dev.erick.roastmytaste.domain.model.UserProfile;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("local")
public class FakeAiAnalyzerProvider implements AiAnalyzisProvider {

    @Override
    public RoastAnalysis analyze(
            UserProfile profile,
            RoastLanguage language
    ) {
        return switch (language) {
            case ENGLISH_UK -> new RoastAnalysis(
                    "Absolute Musical Shambles",
                    "A rather concerning lack of taste.",
                    "Blimey, your playlist sounds like a Spotify "
                            + "algorithm having a proper identity crisis.",
                    "Perhaps let someone with functioning ears "
                            + "choose the next song, mate.",
                    87,
                    List.of("Musical Menace", "Certified Muppet")
            );

            case ENGLISH_US -> new RoastAnalysis(
                    "Playlist Disaster",
                    "Main character syndrome, zero musical direction.",
                    "Bro, your Spotify Wrapped looks like "
                            + "an algorithm lost a bet.",
                    "Try discovering music without TikTok "
                            + "holding your hand.",
                    87,
                    List.of("NPC Energy", "Playlist Criminal")
            );

            case PORTUGUESE -> new RoastAnalysis(
                    "Atentado Contra a Música",
                    "Seu gosto musical precisa de intervenção urgente.",
                    "Meu parceiro, essa playlist parece que foi "
                            + "montada por um algoritmo de ressaca.",
                    "Experimenta ouvir algo que não tenha sido "
                            + "escolhido pelo algoritmo, campeão.",
                    87,
                    List.of("Inimigo do Bom Gosto", "Criminoso Musical")
            );

            case SPANISH -> new RoastAnalysis(
                    "Desastre Musical",
                    "Un gusto musical bastante cuestionable.",
                    "Madre mía, tu playlist parece el resultado "
                            + "de dejar a un algoritmo sin supervisión.",
                    "Prueba a descubrir música sin depender "
                            + "tanto de las recomendaciones.",
                    87,
                    List.of("Desastre Musical", "Sin Salvación")
            );
        };
    }
}
