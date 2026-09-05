package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.AiServiceClient;
import com.ael.algoryqrservice.client.dto.AiMenuImportClientDtos;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.AiMenuImportDtos;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMenuImportServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuService menuService;
    @Mock
    private MenuCategoryService menuCategoryService;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private AiMenuImportService service;

    private final Long menuId = 10L;
    private final Long userId = 7L;

    @Test
    void createJob_whenOwnedMenu_thenProxiesToAiService() {
        UUID jobId = UUID.randomUUID();
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(Menu.builder()
                .menuId(menuId)
                .userId(userId)
                .deleted(false)
                .build()));
        when(aiServiceClient.createMenuImportJob(any())).thenReturn(
                new AiMenuImportClientDtos.JobAccepted(jobId, "queued")
        );

        AiMenuImportDtos.JobAccepted accepted = service.createJob(
                menuId,
                AiMenuImportDtos.CreateJobRequest.builder()
                        .imageUrls(List.of("https://cdn.example/a.jpg"))
                        .build()
        );

        assertThat(accepted.getJobId()).isEqualTo(jobId);
        assertThat(accepted.getStatus()).isEqualTo("queued");
        ArgumentCaptor<AiMenuImportClientDtos.CreateRequest> captor =
                ArgumentCaptor.forClass(AiMenuImportClientDtos.CreateRequest.class);
        verify(aiServiceClient).createMenuImportJob(captor.capture());
        assertThat(captor.getValue().getMenuId()).isEqualTo(menuId);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getImageUrls()).containsExactly("https://cdn.example/a.jpg");
    }

    @Test
    void getJob_whenMenuMismatch_thenForbidden() {
        UUID jobId = UUID.randomUUID();
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(Menu.builder()
                .menuId(menuId)
                .userId(userId)
                .deleted(false)
                .build()));
        when(aiServiceClient.getMenuImportJob(jobId)).thenReturn(
                AiMenuImportClientDtos.JobResponse.builder()
                        .jobId(jobId)
                        .menuId(99L)
                        .userId(userId)
                        .status("queued")
                        .build()
        );

        assertThatThrownBy(() -> service.getJob(menuId, jobId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void publishProducts_whenValid_thenCreatesProduct() {
        when(menuRepository.findById(menuId)).thenReturn(Optional.of(Menu.builder()
                .menuId(menuId)
                .userId(userId)
                .deleted(false)
                .build()));
        when(menuService.createProductForOwner(eq(menuId), eq(userId), any())).thenReturn(
                MenuDtos.MenuProductResponse.builder().productId(99L).menuId(menuId).name("Lahmacun").build()
        );

        AiMenuImportDtos.PublishResponse response = service.publishProducts(
                AiMenuImportDtos.PublishRequest.builder()
                        .menuId(menuId)
                        .userId(userId)
                        .products(List.of(
                                AiMenuImportDtos.PublishProduct.builder()
                                        .name("Lahmacun")
                                        .price(new BigDecimal("120"))
                                        .subCategoryId(5L)
                                        .build()
                        ))
                        .build()
        );

        assertThat(response.getCreatedCount()).isEqualTo(1);
        assertThat(response.getProductIds()).containsExactly(99L);
        verify(menuCategoryService).requireSubCategory(menuId, 5L);
    }
}
