package com.novacart.ai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 商品语义检索专用的 pgvector 数据源。
 *
 * <p>该配置属于统一 AI 模块。连接与订单主库隔离，关闭 RAG 时整个配置不会创建。</p>
 */
@Configuration
@ConditionalOnProperty(name = "novacart.rag.enabled", havingValue = "true", matchIfMissing = true)
public class VectorStoreConfig {
    @Bean(name = "novacartVectorStore")
    public VectorStore novacartVectorStore(
            @Qualifier("dashScopeVisionEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${novacart.vector.url}") String url,
            @Value("${novacart.vector.username}") String username,
            @Value("${novacart.vector.password}") String password,
            @Value("${novacart.vector.dimensions:1024}") int dimensions
    ) {
        // Spring AI 负责文档 Embedding 与相似度查询，业务代码只处理 Product ID。
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        return PgVectorStore.builder(new JdbcTemplate(dataSource), embeddingModel)
                .schemaName("public")
                .vectorTableName("novacart_multimodal_product_vectors")
                .dimensions(dimensions)
                .initializeSchema(true)
                .build();
    }
}
