package com.novacart.ai.rag;

import com.novacart.ai.gateway.AiCommerceGateway;
import com.novacart.ai.gateway.AiContracts;
import com.novacart.ai.service.AiBusinessException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商品知识库的向量化与语义检索服务。
 *
 * <p>向量库只保存商品语义描述和商品 ID，价格、库存、销量等强一致数据始终从
 * 业务库读取。向量库不可用时自动退回关键词检索，不影响基础购物流程。</p>
 */
@Service
public class CatalogRagService {
    private static final String INDEX_MARKER = "novacart:rag:indexed:vision-768";
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final AiCommerceGateway commerceGateway;
    private final StringRedisTemplate redisTemplate;

    public CatalogRagService(
            @Qualifier("novacartVectorStore") ObjectProvider<VectorStore> vectorStoreProvider,
            AiCommerceGateway commerceGateway,
            StringRedisTemplate redisTemplate
    ) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.commerceGateway = commerceGateway;
        this.redisTemplate = redisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexCatalogOnce() {
        // 应用启动且 RAG 可用时自动建库；Redis 标记用于避免每次重启重复消耗
        // Embedding 配额。商品变更后仍可通过管理接口手动重建。
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || Boolean.TRUE.equals(redisTemplate.hasKey(INDEX_MARKER))) {
            return;
        }
        try {
            reindex(vectorStore);
        } catch (Exception ignored) {
            // The catalog remains searchable through SQL tools if vector DB is offline.
        }
    }

    public int reindex() {
        // 手工重建商品向量，便于商品上下架或描述修改后更新 RAG。
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new AiBusinessException("RAG 向量库未启用");
        }
        return reindex(vectorStore);
    }

    public List<Document> semanticSearch(String query, int topK) {
        // 向量库不可用时返回空列表，AI 工具仍可使用业务库关键词检索。
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        return vectorStore == null
                ? List.of()
                : vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build());
    }

    private int reindex(VectorStore vectorStore) {
        // 文档 ID 由 productId 稳定生成，便于重复写入时覆盖同一商品的旧向量。
        List<AiContracts.Product> products = commerceGateway.searchProducts(null, null);
        List<Document> documents = products.stream()
                .map(product -> new Document(
                        UUID.nameUUIDFromBytes(("novacart-product-" + product.id()).getBytes()).toString(),
                        """
                        %s
                        类型：%s
                        分类：%s
                        价格：%s 元
                        介绍：%s
                        标签：%s
                        """.formatted(
                                product.name(),
                                product.bizType(),
                                product.category(),
                                product.price(),
                                product.description(),
                                product.tags()
                        ),
                        Map.of(
                                "productId", product.id(),
                                "bizType", product.bizType(),
                                "category", product.category()
                        )
                ))
                .toList();
        if (!documents.isEmpty()) {
            // 百炼 Embedding 单次最多接收 10 条文本，因此必须分批写入。
            for (int start = 0; start < documents.size(); start += 10) {
                int end = Math.min(start + 10, documents.size());
                vectorStore.add(documents.subList(start, end));
            }
            redisTemplate.opsForValue().set(INDEX_MARKER, "1");
        }
        return documents.size();
    }
}
