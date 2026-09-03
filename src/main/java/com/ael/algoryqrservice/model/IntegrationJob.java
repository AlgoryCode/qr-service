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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "integration_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationJob {

    @Id
    private UUID id;

    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long menuId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 32)
    private String direction;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private Integer snapshotVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode snapshot;

    @Column(length = 128)
    private String externalStoreId;

    @Column(length = 128)
    private String aiBatchId;

    @Column(length = 128)
    private String aiInputFileId;

    @Column(length = 128)
    private String aiOutputFileId;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
