package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.dto.PaymentCardVerificationRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.config.PaymentClientProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PaymentCardDto;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import com.ael.algoryqrservice.model.enums.BillingAddressType;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.catalog.CatalogProducts;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.util.AppTime;
import com.ael.algoryqrservice.util.IdentityNumbers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRequestMapperTest {
    private final PaymentRequestMapper mapper = new PaymentRequestMapper();

    @AfterEach
    void resetClock() {
        AppTime.resetClock();
    }

    @Test
    void newPaymentAttemptId_whenUserIdGiven_thenQrUserIdTimestampFormat() {
        AppTime.setClock(Clock.fixed(Instant.parse("2026-08-23T15:07:45Z"), ZoneId.of("Europe/Istanbul")));

        String attemptId = mapper.newPaymentAttemptId(421L);

        assertThat(attemptId).matches("^qr42120260823180745[a-f0-9]{4}$");
        assertThat(attemptId).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void toThreeDsRequest_whenMonthlySubscription_thenChargeEffectivePrice() {
        PaymentThreeDsRequest result = map(BillingPeriod.MONTHLY);

        assertThat(result.getPrice()).isEqualByComparingTo("99.00");
        assertThat(result.getSubscriptionCycleCount()).isNull();
        assertThat(result.getBillingIntervalMonths()).isEqualTo(1);
        assertThat(result.getBankInstallmentCount()).isNull();
        assertThat(result.getInstallment()).isEqualTo(1);
        assertThat(result.getSourceMetadata().get("installmentNumber")).isEqualTo(1);
        assertThat(result.getSourceMetadata().get("billingPeriod")).isEqualTo("MONTHLY");
    }

    @Test
    void toThreeDsRequest_whenYearlySubscription_thenChargeYearlyPrice() {
        PaymentThreeDsRequest result = map(BillingPeriod.YEARLY);

        assertThat(result.getPrice()).isEqualByComparingTo("999.00");
        assertThat(result.getBillingIntervalMonths()).isEqualTo(12);
        assertThat(result.getSourceMetadata().get("billingPeriod")).isEqualTo("YEARLY");
    }

    @Test
    void toThreeDsRequest_whenNonIdentityBuyerFieldsBlank_thenUsesFallbacks() {
        PurchaseRequest request = new PurchaseRequest();
        request.setPaymentMode(PaymentMode.THREE_DS);
        request.setBillingPeriod(BillingPeriod.MONTHLY);
        PaymentCardDto card = new PaymentCardDto();
        card.setCardHolderName("Ada Lovelace");
        card.setCardNumber("4111111111111111");
        card.setExpireMonth("12");
        card.setExpireYear("2030");
        card.setCvc("123");
        request.setPaymentCard(card);
        PlanPackage plan = PlanPackage.builder().id(2L).code(CatalogPackages.PRO_PACKAGE).name("PRO")
                .price(new BigDecimal("120.00")).currency("TRY").validityDays(30).build();
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .country(" ").city(" ").address(" ").tckn("12345678901").phone("5551112233").build();
        Purchase purchase = Purchase.builder().id(10L).paymentConversationId("conversation")
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .billingPeriod(BillingPeriod.MONTHLY)
                .billingIntervalMonths(1)
                .price(new BigDecimal("120.00"))
                .billingSnapshot(snapshot).build();
        User user = User.builder().id(7L).firstName(" ").lastName(null)
                .email("ada@example.com").build();

        PaymentThreeDsRequest result = mapper.toThreeDsRequest(
                purchase, user, plan, request, "127.0.0.1", new AppProperties()
        );

        assertThat(result.getBuyer().getName()).isEqualTo("Musteri");
        assertThat(result.getBuyer().getSurname()).isEqualTo("Kullanici");
        assertThat(result.getBuyer().getRegistrationAddress()).isEqualTo("Adres bilgisi yok");
        assertThat(result.getBuyer().getCity()).isEqualTo("Istanbul");
        assertThat(result.getBuyer().getCountry()).isEqualTo("Turkey");
        assertThat(result.getBillingAddress().getContactName()).isEqualTo("Musteri");
        assertThat(result.getBuyer().getIdentityNumber()).isEqualTo("12345678901");
        assertThat(result.getBuyer().getGsmNumber()).isEqualTo("5551112233");
        assertThat(result.getSourceMetadata().get("userId")).isEqualTo(7L);
    }

    @Test
    void toThreeDsRequest_whenIdentityNumberMissing_thenUsesDefaultIdentity() {
        PurchaseRequest request = new PurchaseRequest();
        request.setPaymentMode(PaymentMode.THREE_DS);
        request.setBillingPeriod(BillingPeriod.MONTHLY);
        PaymentCardDto card = new PaymentCardDto();
        card.setCardHolderName("Ada Lovelace");
        card.setCardNumber("4111111111111111");
        card.setExpireMonth("12");
        card.setExpireYear("2030");
        card.setCvc("123");
        request.setPaymentCard(card);
        PlanPackage plan = PlanPackage.builder().id(2L).code(CatalogPackages.PRO_PACKAGE).name("PRO")
                .price(new BigDecimal("120.00")).currency("TRY").validityDays(30).build();
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .phone("5551112233").build();
        Purchase purchase = Purchase.builder().id(10L).paymentConversationId("conversation")
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .billingPeriod(BillingPeriod.MONTHLY)
                .billingIntervalMonths(1)
                .price(new BigDecimal("120.00"))
                .billingSnapshot(snapshot).build();
        User user = User.builder().id(7L).firstName("Ada").lastName("Lovelace")
                .email("ada@example.com").build();

        PaymentThreeDsRequest result = mapper.toThreeDsRequest(
                purchase, user, plan, request, "127.0.0.1", new AppProperties()
        );

        assertThat(result.getBuyer().getIdentityNumber()).isEqualTo(IdentityNumbers.DEFAULT);
    }

    @Test
    void toThreeDsRequest_whenPhoneMissing_thenThrowBadRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setPaymentMode(PaymentMode.THREE_DS);
        request.setBillingPeriod(BillingPeriod.MONTHLY);
        PaymentCardDto card = new PaymentCardDto();
        card.setCardHolderName("Ada Lovelace");
        card.setCardNumber("4111111111111111");
        card.setExpireMonth("12");
        card.setExpireYear("2030");
        card.setCvc("123");
        request.setPaymentCard(card);
        PlanPackage plan = PlanPackage.builder().id(2L).code(CatalogPackages.PRO_PACKAGE).name("PRO")
                .price(new BigDecimal("120.00")).currency("TRY").validityDays(30).build();
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .tckn("12345678901").build();
        Purchase purchase = Purchase.builder().id(10L).paymentConversationId("conversation")
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .billingPeriod(BillingPeriod.MONTHLY)
                .billingIntervalMonths(1)
                .price(new BigDecimal("120.00"))
                .billingSnapshot(snapshot).build();
        User user = User.builder().id(7L).firstName("Ada").lastName("Lovelace")
                .email("ada@example.com").build();

        assertThatThrownBy(() -> mapper.toThreeDsRequest(
                purchase, user, plan, request, "127.0.0.1", new AppProperties()
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void toCheckoutFormRequest_whenMonthlySubscription_thenNoCardDataAndSubscriptionMetadata() {
        PlanPackage plan = PlanPackage.builder().id(2L).code(CatalogPackages.PRO_PACKAGE).name("PRO")
                .price(new BigDecimal("120.00")).currency("TRY").validityDays(30).build();
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .name("Ada").surname("Lovelace").country("TR").city("İstanbul")
                .address("Adres").postcode("34000").tckn("12345678901").phone("5551112233").build();
        Purchase purchase = Purchase.builder().id(10L).paymentConversationId("conversation")
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .billingPeriod(BillingPeriod.MONTHLY)
                .billingIntervalMonths(1)
                .price(new BigDecimal("99.00"))
                .billingSnapshot(snapshot).build();
        User user = User.builder().id(7L).firstName("Ada").lastName("Lovelace")
                .email("ada@example.com").phone("5551112233").build();

        PaymentCheckoutFormRequest result = mapper.toCheckoutFormRequest(
                purchase, user, plan, "127.0.0.1", new AppProperties(), new PaymentClientProperties()
        );

        assertThat(result.getPrice()).isEqualByComparingTo("99.00");
        assertThat(result.getPaymentStyle()).isEqualTo("SUBSCRIPTION");
        assertThat(result.getBillingIntervalMonths()).isEqualTo(1);
        assertThat(result.getSubscriptionCycleCount()).isNull();
        assertThat(result.getPaymentGroup()).isEqualTo("SUBSCRIPTION");
        assertThat(result.getConversationId()).isEqualTo("conversation");
        assertThat(result.getBuyer().getIdentityNumber()).isEqualTo("12345678901");
        assertThat(result.getSourceMetadata().get("userId")).isEqualTo(7L);
    }

    @Test
    void toPlanChangeCheckoutFormRequest_whenDifference_thenMarkPlanChangeMetadata() {
        PlanPackage plan = PlanPackage.builder().id(2L).code(CatalogPackages.PRO_PACKAGE).name("PRO")
                .price(new BigDecimal("300.00")).currency("TRY").validityDays(30).build();
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .name("Ada").surname("Lovelace").country("TR").city("İstanbul")
                .address("Adres").postcode("34000").tckn("12345678901").phone("5551112233").build();
        Purchase purchase = Purchase.builder().id(10L).paymentConversationId("planchng-1")
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .billingPeriod(BillingPeriod.MONTHLY)
                .billingIntervalMonths(1)
                .price(new BigDecimal("300.00"))
                .billingSnapshot(snapshot).build();
        User user = User.builder().id(7L).firstName("Ada").lastName("Lovelace")
                .email("ada@example.com").phone("5551112233").build();

        PaymentCheckoutFormRequest result = mapper.toPlanChangeCheckoutFormRequest(
                purchase,
                user,
                plan,
                "127.0.0.1",
                new AppProperties(),
                new PaymentClientProperties(),
                "planchng-1",
                new BigDecimal("80.00")
        );

        assertThat(result.getPrice()).isEqualByComparingTo("80.00");
        assertThat(result.getPaidPrice()).isEqualByComparingTo("80.00");
        assertThat(result.getPaymentStyle()).isEqualTo("ONE_TIME");
        assertThat(result.getBasketId()).isEqualTo("qrplanchng10");
        assertThat(result.getSourceMetadata().get("planChange")).isEqualTo(true);
        assertThat(result.getSourceMetadata().get("planChangeDifference")).isEqualTo(true);
        assertThat(result.getSourceMetadata().get("totalAmount")).isEqualTo(new BigDecimal("80.00"));
    }

    @Test
    void toCardVerificationRequest_whenValid_thenForceNominalMetadataAndNoBasket() {
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .name("Ada").surname("Lovelace").country("TR").city("İstanbul")
                .address("Adres").postcode("34000").tckn("12345678901").phone("5551112233").build();
        User user = User.builder().id(7L).firstName("Ada").lastName("Lovelace")
                .email("ada@example.com").phone("5551112233").build();

        PaymentCardVerificationRequest result = mapper.toCardVerificationRequest(
                user, snapshot, "127.0.0.1", new AppProperties(), "qr-card-verification-7-abcdef12"
        );

        assertThat(result.getConversationId()).isEqualTo("qr-card-verification-7-abcdef12");
        assertThat(result.getSourceReferenceId()).isEqualTo("7");
        assertThat(result.getCurrency()).isEqualTo("TRY");
        assertThat(result.getLocale()).isEqualTo("tr");
        assertThat(result.getBuyer().getIdentityNumber()).isEqualTo("12345678901");
        assertThat(result.getBillingAddress().getCity()).isEqualTo("İstanbul");
    }

    @Test
    void toCardVerificationRequest_whenIdentityMissing_thenUseDefaultIdentity() {
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .phone("5551112233").build();
        User user = User.builder().id(7L).firstName("Ada").lastName("Lovelace")
                .email("ada@example.com").build();

        PaymentCardVerificationRequest result = mapper.toCardVerificationRequest(
                user, snapshot, "127.0.0.1", new AppProperties(), "conv"
        );

        assertThat(result.getBuyer().getIdentityNumber()).isEqualTo(IdentityNumbers.DEFAULT);
    }

    @Test
    void buildCardVerificationConversationId_whenCalledTwice_thenReturnUniqueValuesWithUserPrefix() {
        String first = mapper.buildCardVerificationConversationId(7L);
        String second = mapper.buildCardVerificationConversationId(7L);

        assertThat(first).matches("^qr7\\d{14}[a-f0-9]{4}$");
        assertThat(second).matches("^qr7\\d{14}[a-f0-9]{4}$");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void toAddonCheckoutFormRequest_whenBillingPeriodPresent_thenIncludeInMetadata() {
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .name("Ada").surname("Lovelace").country("TR").city("Istanbul")
                .address("Adres").postcode("34000").tckn("12345678901").build();
        Purchase purchase = Purchase.builder()
                .id(10L)
                .packageId(22L)
                .packageCode(CatalogProducts.QR_MENU)
                .price(new BigDecimal("240.00"))
                .currency("TRY")
                .paymentStyle(PaymentStyle.ONE_TIME)
                .purchaseType(PurchaseType.ADD_ON)
                .billingPeriod(BillingPeriod.YEARLY)
                .billingIntervalMonths(12)
                .billingSnapshot(snapshot)
                .build();
        User user = User.builder().id(7L).firstName("Ada").lastName("Lovelace")
                .email("ada@example.com").phone("5551112233").build();
        LocalDateTime periodStart = LocalDateTime.of(2026, 8, 24, 1, 0);
        LocalDateTime periodEnd = LocalDateTime.of(2027, 8, 24, 1, 0);

        PaymentCheckoutFormRequest result = mapper.toAddonCheckoutFormRequest(
                purchase,
                user,
                CatalogProducts.QR_MENU,
                "QR Menu",
                "127.0.0.1",
                new AppProperties(),
                new PaymentClientProperties(),
                "conv-addon",
                periodStart,
                periodEnd
        );

        assertThat(result.getSourceMetadata().get("billingPeriod")).isEqualTo("YEARLY");
        assertThat(result.getSourceMetadata().get("addon")).isEqualTo(true);
    }

    private PaymentThreeDsRequest map(BillingPeriod period) {
        PurchaseRequest request = new PurchaseRequest();
        request.setPaymentMode(PaymentMode.THREE_DS);
        request.setBillingPeriod(period);
        PaymentCardDto card = new PaymentCardDto();
        card.setCardHolderName("Ada Lovelace");
        card.setCardNumber("4111111111111111");
        card.setExpireMonth("12");
        card.setExpireYear("2030");
        card.setCvc("123");
        request.setPaymentCard(card);
        PlanPackage plan = PlanPackage.builder().id(2L).code(CatalogPackages.PRO_PACKAGE).name("PRO")
                .price(new BigDecimal("120.00")).currency("TRY").validityDays(30).build();
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .name("Ada").surname("Lovelace").country("TR").city("İstanbul")
                .address("Adres").postcode("34000").tckn("12345678901").build();
        BigDecimal charge = period == BillingPeriod.YEARLY
                ? new BigDecimal("999.00")
                : new BigDecimal("99.00");
        Purchase purchase = Purchase.builder().id(10L).paymentConversationId("conversation")
                .paymentStyle(PaymentStyle.SUBSCRIPTION)
                .billingPeriod(period)
                .billingIntervalMonths(period.intervalMonths())
                .price(charge)
                .billingSnapshot(snapshot).build();
        User user = User.builder().id(7L).firstName("Ada").lastName("Lovelace")
                .email("ada@example.com").phone("5551112233").build();
        return mapper.toThreeDsRequest(purchase, user, plan, request, "127.0.0.1", new AppProperties());
    }
}
