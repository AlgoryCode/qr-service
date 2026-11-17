package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.MemberTag;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.AdminAuditLogRepository;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.AdminGrantRepository;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.MemberNoteRepository;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.MemberTagRepository;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCaseRepository;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminMemberServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final MemberTagRepository tags = mock(MemberTagRepository.class);
    private final AdminMemberService service = new AdminMemberService(users, mock(PurchaseRepository.class),
            mock(CollectionCaseRepository.class), mock(MemberNoteRepository.class), tags,
            mock(AdminGrantRepository.class), mock(AdminAuditLogRepository.class));

    @Test
    void tagsAreFoldedWithoutTurkishDotlessI() {
        assertThat(AdminMemberService.normalizeTag("  VIP ")).isEqualTo("vip");
        assertThat(AdminMemberService.normalizeTag("İstanbul   Avrupa")).isEqualTo("istanbul avrupa");
    }

    @Test
    void setTagsKeepsExistingAddsNewAndDropsTheRest() {
        when(users.existsById(1L)).thenReturn(true);
        MemberTag old = MemberTag.builder().merchantId(1L).tag("eski").createdAt(LocalDateTime.now()).build();
        MemberTag kept = MemberTag.builder().merchantId(1L).tag("vip").createdAt(LocalDateTime.now()).build();
        when(tags.findByMerchantIdOrderByTagAsc(1L)).thenReturn(List.of(kept, old));

        List<String> result = service.setTags(1L, List.of("VIP", "Kurumsal", "vip"), "admin");

        assertThat(result).containsExactly("kurumsal", "vip");
        verify(tags).delete(old);
        verify(tags, never()).delete(kept);
        verify(tags).save(any(MemberTag.class));
    }

    @Test
    void rejectsTagsWithOddCharacters() {
        when(users.existsById(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.setTags(1L, List.of("<script>"), "admin"))
                .isInstanceOf(BadRequestException.class);
    }
}
