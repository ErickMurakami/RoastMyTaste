package dev.erick.roastmytaste.interfaces.web;

import dev.erick.roastmytaste.application.port.in.AnalyzeMusicTasteUseCase;
import dev.erick.roastmytaste.domain.enums.RoastLanguage;
import dev.erick.roastmytaste.domain.exception.AiAnalysisException;
import dev.erick.roastmytaste.domain.exception.InsufficientDataException;
import dev.erick.roastmytaste.domain.exception.SpotifyProviderException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class RoastController {

    private static final Logger LOG = LoggerFactory.getLogger(RoastController.class);
    private final AnalyzeMusicTasteUseCase analyzeMusicTaste;

    public RoastController(AnalyzeMusicTasteUseCase analyzeMusicTaste) {
        this.analyzeMusicTaste = analyzeMusicTaste;
    }

    @GetMapping("/roast")
    public ModelAndView roast(Authentication authentication) {
        return page(authentication, RoastLanguage.ENGLISH_UK.code());
    }

    @PostMapping("/roast")
    public ModelAndView generate(
            @RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient client,
            @RequestParam(defaultValue = "en-GB") String language,
            Authentication authentication) {
        var selectedLanguage = RoastLanguage.fromCode(language);
        var result = analyzeMusicTaste.analyze(client.getAccessToken().getTokenValue(), selectedLanguage);
        if (result == null) {
            throw new AiAnalysisException("Empty analysis", null);
        }
        var page = page(authentication, selectedLanguage.code());
        page.addObject("analysis", result);
        return page;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView unsupportedLanguage(Authentication authentication) {
        return error(authentication, "en-GB", HttpStatus.BAD_REQUEST,
                "That language is not on the setlist. Choose one of the four languages below.", false);
    }

    @ExceptionHandler(InsufficientDataException.class)
    public ModelAndView insufficientData(Authentication authentication, HttpServletRequest request) {
        return error(authentication, request.getParameter("language"), HttpStatus.UNPROCESSABLE_CONTENT,
                "Not enough evidence. Spotify needs more listening history before we can judge you. Play some music and come back.", false);
    }

    @ExceptionHandler(SpotifyProviderException.class)
    public ModelAndView spotifyFailure(SpotifyProviderException exception, Authentication authentication,
                                      HttpServletRequest request) {
        boolean reconnect = exception.getCause() instanceof HttpClientErrorException cause
                && (cause.getStatusCode().value() == 401 || cause.getStatusCode().value() == 403);
        return error(authentication, request.getParameter("language"),
                reconnect ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_GATEWAY,
                reconnect ? "Your Spotify connection needs an encore. Reconnect to keep going."
                        : "Spotify is taking an intermission. Give it a moment, then try again.", reconnect);
    }

    @ExceptionHandler(OAuth2AuthorizationException.class)
    public ModelAndView expiredAuthentication(Authentication authentication, HttpServletRequest request) {
        return error(authentication, request.getParameter("language"), HttpStatus.UNAUTHORIZED,
                "Your Spotify connection has expired. Reconnect and we will pick up where we left off.", true);
    }

    @ExceptionHandler(AiAnalysisException.class)
    public ModelAndView aiFailure(Authentication authentication, HttpServletRequest request) {
        return error(authentication, request.getParameter("language"), HttpStatus.BAD_GATEWAY,
                "Our critic lost the plot. We could not finish your roast this time. Give it another go.", false);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpectedFailure(Exception exception, Authentication authentication,
                                          HttpServletRequest request) {
        // Provider exception messages and causes can contain credentials or private listening data.
        LOG.warn("Roast request failed ({})", exception.getClass().getSimpleName());
        return error(authentication, request.getParameter("language"), HttpStatus.INTERNAL_SERVER_ERROR,
                "Something hit a wrong note. Please try again in a moment.", false);
    }

    private ModelAndView error(Authentication authentication, String language, HttpStatus status,
                               String message, boolean reconnect) {
        var page = page(authentication, language);
        page.setStatus(status);
        page.addObject("errorMessage", message);
        page.addObject("reconnect", reconnect);
        return page;
    }

    private ModelAndView page(Authentication authentication, String language) {
        var page = new ModelAndView("roast");
        page.addObject("languages", RoastLanguage.values());
        try {
            page.addObject("selectedLanguage", RoastLanguage.fromCode(language).code());
        } catch (IllegalArgumentException exception) {
            page.addObject("selectedLanguage", RoastLanguage.ENGLISH_UK.code());
        }
        String displayName = "Music enthusiast";
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            Object name = user.getAttribute("display_name");
            if (name instanceof String value && !value.isBlank()) {
                displayName = value;
            }
        }
        page.addObject("displayName", displayName);
        return page;
    }
}
