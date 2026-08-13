package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuProductRating;
import com.ael.algoryqrservice.model.MenuRating;
import com.ael.algoryqrservice.model.dto.AnalyticsDtos;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.repository.MenuProductRatingRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRatingRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuFeedbackServiceTest {

    @Mock
    private MenuRepository menuRepository;
    @Mock
    private MenuRatingRepository menuRatingRepository;
    @Mock
    private MenuProductRatingRepository menuProductRatingRepository;
    @Mock
    private MenuProductRepository menuProductRepository;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private MenuFeedbackService menuFeedbackService;

    @Test
    void listFeedback_whenTypeMenu_thenMapsItems() {
        when(securityUtils.getCurrentUserId()).thenReturn(9L);
        when(menuRepository.findById(5L)).thenReturn(Optional.of(ownedMenu()));
        MenuRating rating = MenuRating.builder()
                .id(1L)
                .menuId(5L)
                .score((short) 4)
                .comment("İyi")
                .deviceType("MOBILE")
                .createdAt(LocalDateTime.of(2026, 8, 1, 12, 0))
                .build();
        when(menuRatingRepository.findForOwner(eq(5L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rating)));

        MenuDtos.FeedbackPageResponse page = menuFeedbackService.listFeedback(
                5L, "menu", null, null, null, 0, 20
        );

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getType()).isEqualTo("menu");
        assertThat(page.getContent().getFirst().getScore()).isEqualTo(4);
        assertThat(page.getContent().getFirst().getComment()).isEqualTo("İyi");
    }

    @Test
    void buildReportFeedback_whenPeriodHasData_thenReturnsSummaries() {
        when(menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(5L))
                .thenReturn(List.of(MenuProduct.builder().productId(11L).menuId(5L).name("Çay").build()));
        when(menuRatingRepository.averageScoreByMenuIdAndPeriod(eq(5L), any(), any())).thenReturn(4.5);
        when(menuRatingRepository.countByMenuIdAndPeriod(eq(5L), any(), any())).thenReturn(2L);
        when(menuRatingRepository.scoreHistogramByMenuIdAndPeriod(eq(5L), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{(short) 4, 1L}, new Object[]{(short) 5, 1L}));
        when(menuRatingRepository.sampleCommentsByMenuIdAndPeriod(eq(5L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(MenuRating.builder()
                        .score((short) 4)
                        .comment("Genel iyi")
                        .createdAt(LocalDateTime.now())
                        .build()));

        when(menuProductRatingRepository.averageScoreByMenuIdAndPeriod(eq(5L), any(), any())).thenReturn(3.0);
        when(menuProductRatingRepository.countByMenuIdAndPeriod(eq(5L), any(), any())).thenReturn(1L);
        when(menuProductRatingRepository.scoreHistogramByMenuIdAndPeriod(eq(5L), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{(short) 3, 1L}));
        when(menuProductRatingRepository.topRatedProductsByPeriod(eq(5L), any(), any(), eq(1L), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{11L, 3.0, 1L}));
        when(menuProductRatingRepository.bottomRatedProductsByPeriod(eq(5L), any(), any(), eq(1L), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{11L, 3.0, 1L}));
        when(menuProductRatingRepository.sampleCommentsByMenuIdAndPeriod(eq(5L), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(MenuProductRating.builder()
                        .menuProductId(11L)
                        .score((short) 3)
                        .comment("Ilık geldi")
                        .createdAt(LocalDateTime.now())
                        .build()));

        AnalyticsDtos.ReportFeedback feedback = menuFeedbackService.buildReportFeedback(
                5L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 11)
        );

        assertThat(feedback.menu().ratingCount()).isEqualTo(2L);
        assertThat(feedback.menu().ratingAvg()).isEqualByComparingTo("4.50");
        assertThat(feedback.menu().sampleComments()).hasSize(1);
        assertThat(feedback.products().ratingCount()).isEqualTo(1L);
        assertThat(feedback.products().topRated().getFirst().name()).isEqualTo("Çay");
        assertThat(feedback.products().sampleComments().getFirst().comment()).isEqualTo("Ilık geldi");
    }

    private Menu ownedMenu() {
        return Menu.builder()
                .menuId(5L)
                .userId(9L)
                .qrId(1L)
                .themeId("soft")
                .businessName("Test")
                .active(true)
                .publicAccessEnabled(true)
                .ratingAvg(BigDecimal.ZERO)
                .ratingCount(0L)
                .build();
    }
}
