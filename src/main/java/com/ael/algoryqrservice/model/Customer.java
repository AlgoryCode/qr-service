package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_customer", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "phone"),
        @UniqueConstraint(columnNames = "provider_subject")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true)
    private String phone;

    @Column
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    @ColumnDefault("'BASIC'")
    @Builder.Default
    private AuthProvider provider = AuthProvider.BASIC;

    @Column(name = "provider_subject", unique = true, length = 128)
    private String providerSubject;

    @Column(name = "avatar_key", length = 64)
    private String avatarKey;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getDisplayName() {
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            name.append(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(lastName.trim());
        }
        return name.toString();
    }
}
