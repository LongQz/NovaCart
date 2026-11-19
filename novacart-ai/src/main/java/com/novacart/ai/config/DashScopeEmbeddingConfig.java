package com.novacart.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/** Creates the native DashScope multimodal embedding bean. */
@Configuration
public class DashScopeEmbeddingConfig {
    @Bean("dashScopeVisionEmbeddingModel")
    public EmbeddingModel dashScopeVisionEmbeddingModel(
            ObjectMapper objectMapper,
            @Value("${novacart.embedding.base-url}") String baseUrl,
            @Value("${novacart.embedding.api-key}") String apiKey,
            @Value("${novacart.embedding.model}") String model,
            @Value("${novacart.embedding.dimensions:768}") int dimensions
    ) {
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        return new DashScopeVisionEmbeddingModel(client, objectMapper, model, dimensions);
    }
}
