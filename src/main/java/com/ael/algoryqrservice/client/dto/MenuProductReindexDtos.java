package com.ael.algoryqrservice.client.dto;

import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public final class MenuProductReindexDtos {

    private MenuProductReindexDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
            List<MenuProductDocumentMessage> products,
            Long purgeMissingForMenuId,
            List<Long> keepProductIds
    ) {
    }

    public record Response(
            int indexed,
            int purged,
            String embeddingModel
    ) {
    }
}
