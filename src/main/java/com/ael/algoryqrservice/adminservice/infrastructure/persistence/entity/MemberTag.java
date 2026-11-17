package com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.io.Serializable;

/** Free-form admin label on a member (VIP, riskli, kurumsal...). */
@Entity
@Table(name = "member_tag")
@IdClass(MemberTag.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberTag {

    @Id
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Id
    @Column(nullable = false, length = 40)
    private String tag;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public record Key(Long merchantId, String tag) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
