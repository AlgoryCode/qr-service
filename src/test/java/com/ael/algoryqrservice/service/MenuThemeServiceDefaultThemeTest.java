package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogThemes;
import com.ael.algoryqrservice.model.MenuTheme;
import com.ael.algoryqrservice.model.UserThemeAssignment;
import com.ael.algoryqrservice.repository.MenuThemeRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.repository.UserThemeAssignmentRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuThemeServiceDefaultThemeTest {

    @Mock
    MenuThemeRepository menuThemeRepository;
    @Mock
    UserThemeAssignmentRepository userThemeAssignmentRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    SecurityUtils securityUtils;

    @InjectMocks
    MenuThemeService menuThemeService;

    @Test
    void ensureDefaultThemeAssigned_whenNoAssignments_thenAssignLuxury() {
        MenuTheme luxury = MenuTheme.builder().id(30L).code(CatalogThemes.DEFAULT_THEME_CODE).active(true).build();
        when(userRepository.existsById(7L)).thenReturn(true);
        when(userThemeAssignmentRepository.findByUserId(7L)).thenReturn(List.of()).thenReturn(List.of(
                UserThemeAssignment.builder().userId(7L).themeId(30L).build()
        ));
        when(menuThemeRepository.findByCodeIn(any())).thenReturn(List.of(luxury));
        when(userThemeAssignmentRepository.existsByUserIdAndThemeId(7L, 30L)).thenReturn(false);
        when(menuThemeRepository.findByIdInAndActiveTrueOrderBySortOrderAscIdAsc(Set.of(30L)))
                .thenReturn(List.of(luxury));

        menuThemeService.ensureDefaultThemeAssigned(7L);

        ArgumentCaptor<UserThemeAssignment> captor = ArgumentCaptor.forClass(UserThemeAssignment.class);
        verify(userThemeAssignmentRepository).save(captor.capture());
        assertThat(captor.getValue().getThemeId()).isEqualTo(30L);
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    }

    @Test
    void ensureDefaultThemeAssigned_whenAlreadyAssigned_thenSkip() {
        when(userRepository.existsById(7L)).thenReturn(true);
        when(userThemeAssignmentRepository.findByUserId(7L)).thenReturn(List.of(
                UserThemeAssignment.builder().userId(7L).themeId(11L).build()
        ));
        when(menuThemeRepository.findByIdInAndActiveTrueOrderBySortOrderAscIdAsc(Set.of(11L)))
                .thenReturn(List.of(MenuTheme.builder().id(11L).code("soft").active(true).build()));

        menuThemeService.ensureDefaultThemeAssigned(7L);

        verify(userThemeAssignmentRepository, never()).save(any());
    }
}
