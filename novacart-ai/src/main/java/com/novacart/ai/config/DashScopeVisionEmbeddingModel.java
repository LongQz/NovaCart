package com.novacart.ai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring AI adapter for Alibaba Bailian's native multimodal embedding API.
 *
 * <p>{@code tongyi-embedding-vision-flash} is not exposed by the OpenAI-compatible
 * endpoint, so text embeddings must call the DashScope native service.</p>
 */
public class DashScopeVisionEmbeddingModel implements EmbeddingModel {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int dimensions;

    public DashScopeVisionEmbeddingModel(
            RestClient restClient,
            ObjectMapper objectMapper,
            String model,
            int dimensions
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Map<String, String>> contents = request.getInstructions().stream()
                .map(text -> Map.of("text", text))
                .toList();
        String body;
        try {
            body = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "input", Map.of("contents", contents)))
                    .retrieve()
                    .body(String.class);
            JsonNode embeddingsNode = objectMapper.readTree(body).path("output").path("embeddings");
            List<Embedding> embeddings = new ArrayList<>();
            for (int index = 0; index < embeddingsNode.size(); index++) {
                JsonNode vectorNode = embeddingsNode.get(index).path("embedding");
                float[] vector = new float[vectorNode.size()];
                for (int i = 0; i < vectorNode.size(); i++) {
                    vector[i] = (float) vectorNode.get(i).asDouble();
                }
                embeddings.add(new Embedding(vector, index));
            }
            return new EmbeddingResponse(embeddings);
        } catch (Exception exception) {
            throw new IllegalStateException("DashScope multimodal embedding failed", exception);
        }
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
