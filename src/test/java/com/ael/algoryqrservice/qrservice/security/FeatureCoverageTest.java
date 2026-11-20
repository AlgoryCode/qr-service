package com.ael.algoryqrservice.qrservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every controller either checks the merchant's plan with {@link RequiresFeature} or is listed
 * here with the reason it does not. A new controller without either fails the build, so a paid
 * feature cannot ship with only a frontend lock.
 */
class FeatureCoverageTest {

    private static final String ROOT = "com.ael.algoryqrservice";

    /** Whole areas that run before or outside a plan: sign-in, buying a plan, admin, service-to-service. */
    private static final Set<String> EXEMPT_PACKAGES = Set.of(
            ROOT + ".authservice.",
            ROOT + ".adminservice.",
            ROOT + ".paymentservice.",
            ROOT + ".qrservice.billing.",
            ROOT + ".fulfillmentservice."
    );

    /**
     * Controllers that stay ungated, with why. "İncelenecek" marks ones that were ungated when this
     * test was written and still deserve a decision; gate them and drop the line.
     */
    private static final Map<String, String> EXEMPT = Map.ofEntries(
            entry("staffservice.presentation.controller.StaffCourierController", "Personel oturumu; İncelenecek"),
            entry("staffservice.presentation.controller.StaffOrderFlowController", "Personel oturumu; İncelenecek"),
            entry("staffservice.presentation.controller.StaffKitchenController", "Personel oturumu; İncelenecek"),
            entry("staffservice.presentation.controller.StaffCampaignController", "Personel oturumu; İncelenecek"),
            entry("staffservice.presentation.controller.StaffOrderController", "Personel oturumu; İncelenecek"),
            entry("staffservice.presentation.controller.StaffCommissionController", "Personel oturumu; İncelenecek"),
            entry("staffservice.presentation.controller.StaffOpsController", "Personel oturumu; İncelenecek"),
            entry("billservice.presentation.controller.StaffBillController", "Personel oturumu; İncelenecek"),
            entry("billservice.presentation.controller.StaffTableBillController", "Personel oturumu; İncelenecek"),
            entry("customerservice.presentation.controller.CustomerAccountController", "Misafir müşteri hesabı"),
            entry("merchantservice.presentation.controller.MerchantController", "İşletme profili, her pakette"),
            entry("qrservice.order.controller.CustomerOrderController", "Misafir sipariş"),
            entry("qrservice.order.controller.PublicOrderController", "Herkese açık menü"),
            entry("qrservice.order.controller.PublicTableSessionController", "Herkese açık menü"),
            entry("qrservice.integration.yemeksepeti.controller.YemekSepetiWebhookController", "Sağlayıcı webhook'u, imza ile"),
            entry("qrservice.integration.ubereats.controller.UberEatsWebhookController", "Sağlayıcı webhook'u, imza ile"),
            entry("qrservice.qr.controller.FeatureQrController", "QR_CREATE hakkı serviste kota ile düşülür"),
            entry("qrservice.qr.controller.QrPublicController", "Herkese açık QR yönlendirme"),
            entry("qrservice.console.controller.PublicDeviceController", "Cihaz QR sayfası; eklenti serviste kontrol edilir"),
            entry("qrservice.print.controller.PrintAgentController", "Yazıcı ajanı kendi anahtarıyla"),
            entry("qrservice.common.controller.HealthCheckController", "Sağlık kontrolü"),
            entry("qrservice.platform.controller.PlatformFeedbackController", "Ürün geri bildirimi, her pakette"),
            entry("qrservice.platform.controller.MemberContentController", "Duyurular ve SSS, her pakette"),
            entry("qrservice.menu.controller.MenuLogoController", "Menü düzenleme; İncelenecek"),
            entry("qrservice.menu.controller.MenuCategoryController", "Menü düzenleme; İncelenecek"),
            entry("qrservice.menu.controller.MenuCoverController", "Menü düzenleme; İncelenecek"),
            entry("qrservice.menu.controller.MenuTaxonomyController", "Menü düzenleme; İncelenecek"),
            entry("qrservice.menu.controller.MenuThemeController", "CUSTOM_DESIGN serviste kontrol edilir"),
            entry("qrservice.menu.controller.PublicMenuThemeController", "Herkese açık menü"),
            entry("qrservice.product.controller.ProductImageController", "Menü düzenleme; İncelenecek"),
            entry("qrservice.account.controller.AuthEmailVerificationController", "Giriş öncesi"),
            entry("qrservice.account.controller.AccountController", "Hesap ayarları, her pakette"),
            entry("qrservice.account.controller.AccessSessionController", "Paketin kendisini okur"),
            entry("qrservice.campaign.controller.CampaignClaimController", "Misafir kampanya kullanımı"),
            entry("qrservice.campaign.controller.PublicCampaignController", "Herkese açık menü"),
            entry("qrservice.store.controller.StorefrontController", "Herkese açık mağaza"),
            entry("qrservice.analytics.controller.SiteAnalyticsController", "Ziyaret ölçümü"),
            entry("qrservice.branch.capacity.CapacityController", "Paket düşüşünde şube seçimi, her pakette"),
            entry("qrservice.usage.UsageController", "Dönemlik kotaları okur ve ölçer, her pakette"),
            entry("qrservice.analytics.assistant.AssistantReportController", "Asistan raporu ve kullanım kaydı, her pakette; şube sahipliği serviste kontrol edilir"),
            entry("qrservice.analytics.controller.MenuFixedExpenseController", "SMART_REPORTING/WAITER_PANEL içeride kontrol edilir")
    );

    @Test
    void scanFindsTheControllers() {
        assertThat(controllers()).hasSizeGreaterThan(50);
    }

    @Test
    void everyControllerChecksThePlanOrSaysWhyNot() {
        Set<String> ungated = controllers().stream()
                .filter(type -> !gated(type))
                .map(Class::getName)
                .filter(name -> EXEMPT_PACKAGES.stream().noneMatch(name::startsWith))
                .filter(name -> !name.contains(".internal."))
                .map(name -> name.substring(ROOT.length() + 1))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(ungated)
                .as("Ungated controllers: add @RequiresFeature, or list them in EXEMPT with a reason")
                .isSubsetOf(EXEMPT.keySet());
    }

    @Test
    void exemptListHasNoStaleEntries() {
        Set<String> known = controllers().stream()
                .filter(type -> !gated(type))
                .map(type -> type.getName().substring(ROOT.length() + 1))
                .collect(Collectors.toSet());

        assertThat(EXEMPT.keySet())
                .as("These are gated or gone now; remove them from EXEMPT")
                .isSubsetOf(known);
    }

    private static Set<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        Set<Class<?>> types = new TreeSet<>((a, b) -> a.getName().compareTo(b.getName()));
        for (BeanDefinition definition : scanner.findCandidateComponents(ROOT)) {
            try {
                Class<?> type = Class.forName(definition.getBeanClassName());
                if (!type.isAnnotationPresent(ControllerAdvice.class) && !type.isAnnotationPresent(RestControllerAdvice.class)) {
                    types.add(type);
                }
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException(exception);
            }
        }
        return types;
    }

    private static boolean gated(Class<?> type) {
        return type.isAnnotationPresent(RequiresFeature.class)
                || Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> method.isAnnotationPresent(RequiresFeature.class));
    }
}
