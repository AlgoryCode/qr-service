package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.CustomerMembership;
import com.ael.algoryqrservice.model.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerMembershipRepository extends JpaRepository<CustomerMembership, Long> {

    Optional<CustomerMembership> findByCustomerIdAndMenuId(Long customerId, Long menuId);

    boolean existsByCustomerIdAndMenuId(Long customerId, Long menuId);

    List<CustomerMembership> findByMenuIdAndStatusOrderByJoinedAtDesc(Long menuId, MembershipStatus status);

    List<CustomerMembership> findByBusinessIdAndStatusOrderByJoinedAtDesc(Long businessId, MembershipStatus status);
}
