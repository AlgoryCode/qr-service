package com.ael.algoryqrservice.demo.provision;

import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@RequiredArgsConstructor
public class DemoProvisionContext {

    public static final String BRANCH_NAME = "Algory Demo Şube";
    public static final String MENU_BUSINESS_NAME = "Algory Demo Menü";

    private final Long userId;
    private final String displayName;

    @Setter
    private DemoMenuPresentation presentation;
    @Setter
    private String themeCode;
    @Setter
    private Branch branch;
    @Setter
    private Menu menu;
    @Setter
    private Long demoTableId;
    @Setter
    private int seededProductCount;
    @Setter
    private int seededBillCount;
}
