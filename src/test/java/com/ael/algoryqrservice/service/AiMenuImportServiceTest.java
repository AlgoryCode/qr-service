package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.messaging.AiMenuImportMessagePublisher;
import com.ael.algoryqrservice.model.AiMenuImportDraft;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.dto.AiMenuImportDtos;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.enums.AiMenuImportJobStatus;
import com.ael.algoryqrservice.repository.AiMenuImportDraftRepository;
import com.ael.algoryqrservice.repository.AiMenuImportJobRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMenuImportServiceTest {

    @Mock
    private AiMenuImportJobRepository jobRepository;
    @Mock
    private AiMenuImportDraftRepository draftRepository;
    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuService menuService;
    @Mock
    private MenuCategoryService menuCategoryService;
    @Mock
    private AiMenuImportMessagePublisher messagePublisher;
    @Mock
    private SecurityUtils securityUtils;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiMenuImportService service;

    private final Long menuId = 10L;
    private final Long userId = 7L;
    private final UUID draftId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        doNothing().when(menuService).requireOwnedMenu(menuId);
    }

    @Test
    void approve_whenDraftValid_thenCreatesProduct() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("name", "Lahmacun");
        data.put("price", 120);
        data.put("currency", "TRY");
        data.put("subCategoryId", 5);
        data.put("description", "İnce hamur");

        AiMenuImportDraft draft = draft(data, AiMenuImportJobStatus.WAITING_APPROVAL);
        when(draftRepository.findByIdAndMenuId(draftId, menuId)).thenReturn(Optional.of(draft));
        when(menuCategoryService.requireSubCategory(menuId, 5L))
                .thenReturn(MenuSubCategory.builder().id(5L).menuId(menuId).menuCategoryId(1L).name("Ana").slug("ana").sortOrder(0).build());
        when(menuService.createProduct(eq(menuId), any())).thenReturn(
                MenuDtos.MenuProductResponse.builder().productId(99L).menuId(menuId).name("Lahmacun").build()
        );
        when(draftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiMenuImportDtos.DraftResponse response = service.approve(menuId, draftId, userId);

        assertThat(response.getApprovalStatus()).isEqualTo(AiMenuImportJobStatus.APPROVED);
        assertThat(response.getPublishedProductId()).isEqualTo(99L);
        ArgumentCaptor<MenuDtos.MenuProductRequest> captor = ArgumentCaptor.forClass(MenuDtos.MenuProductRequest.class);
        verify(menuService).createProduct(eq(menuId), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Lahmacun");
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("120");
    }

    @Test
    void approve_whenPriceMissing_thenThrow() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("name", "Lahmacun");
        data.put("subCategoryId", 5);
        AiMenuImportDraft draft = draft(data, AiMenuImportJobStatus.WAITING_APPROVAL);
        when(draftRepository.findByIdAndMenuId(draftId, menuId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.approve(menuId, draftId, userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Fiyat");
        verify(menuService, never()).createProduct(any(), any());
    }

    @Test
    void reject_whenWaiting_thenMarksRejected() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("name", "Lahmacun");
        AiMenuImportDraft draft = draft(data, AiMenuImportJobStatus.WAITING_APPROVAL);
        when(draftRepository.findByIdAndMenuId(draftId, menuId)).thenReturn(Optional.of(draft));
        when(draftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reject(menuId, draftId, AiMenuImportDtos.RejectRequest.builder().reason("istemiyorum").build());

        ArgumentCaptor<AiMenuImportDraft> captor = ArgumentCaptor.forClass(AiMenuImportDraft.class);
        verify(draftRepository).save(captor.capture());
        assertThat(captor.getValue().getApprovalStatus()).isEqualTo(AiMenuImportJobStatus.REJECTED);
        assertThat(captor.getValue().getRejectReason()).isEqualTo("istemiyorum");
        assertThat(captor.getValue().getPublishedProductId()).isNull();
    }

    private AiMenuImportDraft draft(ObjectNode data, String status) {
        return AiMenuImportDraft.builder()
                .id(draftId)
                .jobId(UUID.randomUUID())
                .tenantId(userId)
                .menuId(menuId)
                .sourceProductId("p1")
                .productData(data)
                .confidence(BigDecimal.valueOf(0.9))
                .approvalStatus(status)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
