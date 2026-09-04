package com.ael.algoryqrservice.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_menu_import_drafts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMenuImportDraft {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long menuId;

    @Column(nullable = false, length = 128)
    private String sourceProductId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode productData;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(nullable = false, length = 32)
    private String approvalStatus;

    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long publishedProductId;

    @Column(columnDefinition = "text")
    private String rejectReason;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
