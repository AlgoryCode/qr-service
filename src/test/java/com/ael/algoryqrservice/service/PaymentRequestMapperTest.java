package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.client.dto.PaymentCardVerificationRequest;
import com.ael.algoryqrservice.client.dto.PaymentCheckoutFormRequest;
import com.ael.algoryqrservice.client.dto.PaymentThreeDsRequest;
import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.BillingSnapshot;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.PaymentCardDto;
import com.ael.algoryqrservice.model.dto.PurchaseRequest;
import com.ael.algoryqrservice.model.enums.BillingAddressType;
import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PaymentMode;
import com.ael.algoryqrservice.model.enums.PaymentStyle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRequestMapperTest {
    private final PaymentRequestMapper mapper = new PaymentRequestMapper();

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
    void toThreeDsRequest_whenIdentityNumberMissing_thenThrowBadRequest() {
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

        assertThatThrownBy(() -> mapper.toThreeDsRequest(
                purchase, user, plan, request, "127.0.0.1", new AppProperties()
        )).isInstanceOf(BadRequestException.class);
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
                purchase, user, plan, "127.0.0.1", new AppProperties()
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
    void toCardVerificationRequest_whenIdentityMissing_thenThrowBadRequest() {
        BillingSnapshot snapshot = BillingSnapshot.builder().type(BillingAddressType.INDIVIDUAL)
                .phone("5551112233").build();
        User user = User.builder().id(7L).firstName("Ada").lastName("Lovelace")
                .email("ada@example.com").build();

        assertThatThrownBy(() -> mapper.toCardVerificationRequest(
                user, snapshot, "127.0.0.1", new AppProperties(), "conv"
        )).isInstanceOf(BadRequestException.class);
    }

    @Test
    void buildCardVerificationConversationId_whenCalledTwice_thenReturnUniqueValuesWithUserPrefix() {
        String first = mapper.buildCardVerificationConversationId(7L);
        String second = mapper.buildCardVerificationConversationId(7L);

        assertThat(first).startsWith("qr-card-verification-7-");
        assertThat(second).startsWith("qr-card-verification-7-");
        assertThat(first).isNotEqualTo(second);
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
