package com.ael.algoryqrservice.demo.provision;

import com.ael.algoryqrservice.catalog.CatalogThemes;
import com.ael.algoryqrservice.model.MenuTheme;
import com.ael.algoryqrservice.repository.MenuThemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class DemoThemePicker {

    private final MenuThemeRepository menuThemeRepository;

    public String pickThemeCode(DemoMenuPresentation presentation) {
        List<String> fromFixture = resolvePreferred(presentation);
        if (!fromFixture.isEmpty()) {
            return fromFixture.get(ThreadLocalRandom.current().nextInt(fromFixture.size()));
        }
        List<MenuTheme> active = menuThemeRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc();
        if (active.isEmpty()) {
            return CatalogThemes.DEFAULT_THEME_CODE;
        }
        return active.get(ThreadLocalRandom.current().nextInt(active.size())).getCode();
    }

    private List<String> resolvePreferred(DemoMenuPresentation presentation) {
        if (presentation == null || presentation.preferredThemeCodes() == null) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (String raw : presentation.preferredThemeCodes()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String code = raw.trim().toLowerCase(Locale.ROOT);
            if (menuThemeRepository.findByCodeIgnoreCase(code).filter(MenuTheme::isActive).isPresent()) {
                codes.add(code);
            }
        }
        return codes;
    }
}
