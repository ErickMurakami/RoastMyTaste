package dev.erick.roastmytaste.infrastructure.config;

import dev.erick.roastmytaste.infrastructure.spotify.SpotifyApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class SpotifyClientConfig {
    @Bean
    RestClient spotifyRestClient(
            @Value("${spotify.api.base-url}") String baseUrl,
            RestClient.Builder builder
    ) {
        return builder
                .baseUrl(baseUrl).build();
    }

    @Bean
    SpotifyApiClient spotifyApiClient(RestClient spotifyRestClient) {
        var adapter = RestClientAdapter.create(spotifyRestClient);

        var factory = HttpServiceProxyFactory
                .builderFor(adapter)
                .build();

        return factory.createClient(SpotifyApiClient.class);
    }
}
