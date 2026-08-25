package com.ael.algoryqrservice.service.menuindex;

import com.ael.algoryqrservice.client.AiServiceClient;
import com.ael.algoryqrservice.client.dto.MenuProductReindexDtos;
import com.ael.algoryqrservice.config.AiServiceProperties;
import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Rebuilds the whole vector index for one menu. Incremental edits flow through
 * {@link MenuProductIndexNotifier}; this exists for first-time seeding and repair.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuProductReindexService {

    private final MenuProductRepository menuProductRepository;
    private final MenuProductDocumentFactory documentFactory;
    private final AiServiceClient aiServiceClient;
    private final AiServiceProperties properties;

    public record ReindexSummary(long menuId, int products, int indexed, int purged) {
    }

    @Transactional(readOnly = true)
    public List<MenuProductDocumentMessage> collectDocuments(Long menuId) {
        List<MenuProduct> products =
                menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId);
        return documentFactory.createAll(products);
    }

    public ReindexSummary reindexMenu(Long menuId) {
        List<MenuProductDocumentMessage> documents = collectDocuments(menuId);
        if (documents.isEmpty()) {
            log.info("Menu {} has no indexable products; nothing sent to the vector index", menuId);
            return new ReindexSummary(menuId, 0, 0, 0);
        }

        int batchSize = Math.max(1, properties.getReindexBatchSize());
        List<Long> allProductIds = documents.stream()
                .map(MenuProductDocumentMessage::productId)
                .toList();
        int indexed = 0;
        int purged = 0;

        for (int start = 0; start < documents.size(); start += batchSize) {
            List<MenuProductDocumentMessage> batch =
                    documents.subList(start, Math.min(start + batchSize, documents.size()));
            boolean lastBatch = start + batchSize >= documents.size();
            MenuProductReindexDtos.Response response = aiServiceClient.reindex(
                    batch,
                    lastBatch ? menuId : null,
                    lastBatch ? allProductIds : null
            );
            if (response != null) {
                indexed += response.indexed();
                purged += response.purged();
            }
        }

        log.info("Menu {} reindexed: products={} indexed={} purged={}",
                menuId, documents.size(), indexed, purged);
        return new ReindexSummary(menuId, documents.size(), indexed, purged);
    }
}
