package com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.MemberTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface MemberTagRepository extends JpaRepository<MemberTag, MemberTag.Key> {

    List<MemberTag> findByMerchantIdOrderByTagAsc(Long merchantId);

    List<MemberTag> findByMerchantIdIn(Collection<Long> merchantIds);

    List<MemberTag> findByTag(String tag);

    void deleteByMerchantId(Long merchantId);

    @Query("select t.tag as tag, count(t) as count from MemberTag t group by t.tag order by count(t) desc, t.tag asc")
    List<TagCount> countByTag();

    interface TagCount {
        String getTag();

        long getCount();
    }
}
