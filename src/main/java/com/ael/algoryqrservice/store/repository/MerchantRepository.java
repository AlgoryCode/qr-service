package com.ael.algoryqrservice.store.repository;

import com.ael.algoryqrservice.store.model.Merchant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByUserIdAndDeletedFalse(Long userId);

    Optional<Merchant> findByIdAndDeletedFalse(Long id);

    Optional<Merchant> findByStoreNoAndDeletedFalse(Long storeNo);

    Optional<Merchant> findByCatalogMenuIdAndDeletedFalse(Long catalogMenuId);

    boolean existsByUserIdAndDeletedFalse(Long userId);

    boolean existsByPublicToken(String publicToken);

    @Query(value = "select nextval('seq_merchant_store_no')", nativeQuery = true)
    Long nextStoreNo();

    /** Order numbers come from a per-merchant counter, so the row must be locked while it advances. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select merchant from Merchant merchant where merchant.id = :id")
    Optional<Merchant> lockById(@Param("id") Long id);
}
