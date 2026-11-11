package dev.erick.roastmytaste.interfaces.rest;

import dev.erick.roastmytaste.domain.exception.AiAnalysisException;
import dev.erick.roastmytaste.domain.exception.InsufficientDataException;
import dev.erick.roastmytaste.domain.exception.SpotifyProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@RestControllerAdvice(basePackageClasses = SpotifyAnalyzisDebugController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(InsufficientDataException.class)
    public ResponseEntity<ApiError> handleInsufficientData(InsufficientDataException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiError.of("INSUFFICIENT_DATA", ex.getMessage()));
    }

    @ExceptionHandler(SpotifyProviderException.class)
    public ResponseEntity<ApiError> handleSpotifyProviderException(SpotifyProviderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of("SPOTIFY_PROVIDER_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(AiAnalysisException.class)
    public ResponseEntity<ApiError> handleAiAnalysisException(AiAnalysisException ex) {
        LOG.warn("AI analysis failed ({})", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of("AI_ANALYSIS_ERROR", "The AI provider failed to generate a valid roast analysis."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_SERVER_ERROR", "Unexpected server error."));
    }

    public record ApiError(
            Instant timestamp,
            String code,
            String message
    ){
        public static ApiError of(String code, String message) {
            return new ApiError(Instant.now(), code, message);
        }
    }
}
