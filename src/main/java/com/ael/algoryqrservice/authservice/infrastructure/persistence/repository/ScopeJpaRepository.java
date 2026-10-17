package com.ael.algoryqrservice.authservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.ScopeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import com.ael.algoryqrservice.authservice.infrastructure.persistence.entity.RoleScopeEntity;

public interface ScopeJpaRepository extends JpaRepository<ScopeEntity, Long> {

    @Query("""
            select s.code from ScopeEntity s, RoleScopeEntity rs
            where rs.scopeId = s.id
              and rs.roleId = :roleId
              and s.active = true
            order by s.code
            """)
    List<String> findCodesByRoleId(@Param("roleId") Long roleId);
}
