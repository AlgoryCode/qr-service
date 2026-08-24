package com.ael.algoryqrservice.integration.odeal.service;

import com.ael.algoryqrservice.integration.odeal.client.OdealClient;
import com.ael.algoryqrservice.integration.odeal.config.OdealProperties;
import com.ael.algoryqrservice.integration.odeal.model.dto.OdealTestDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OdealTestService {

    public static final String TEST_KEY_HEADER = "X-Odeal-Test-Key";

    private final OdealClient odealClient;
    private final OdealProperties properties;
    private final ObjectMapper objectMapper;

    public void validateTestApiKey(String testApiKey) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ödeal test endpoint'leri devre dışı");
        }
        String expected = properties.getTestApiKey();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ODEAL_TEST_API_KEY yapılandırılmamış");
        }
        if (testApiKey == null || testApiKey.isBlank() || !expected.equals(testApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Geçersiz test API anahtarı");
        }
    }

    public OdealTestDtos.ProxyResponse getUnits() {
        return odealClient.getUnits();
    }

    public OdealTestDtos.ProxyResponse sendBasket(JsonNode body) {
        if (body == null || body.isNull() || body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sepet gövdesi boş olamaz");
        }
        return odealClient.sendBasket(body);
    }

    public OdealTestDtos.ProxyResponse sendSampleBasket(HttpServletRequest request) {
        String paymentType = resolvePaymentType(request);
        String amount = resolveAmount(request);
        JsonNode payload = buildSampleBasket(paymentType, parseAmount(amount));
        return odealClient.sendBasket(payload);
    }

    String resolvePaymentType(HttpServletRequest request) {
        for (String candidate : collectParameterCandidates(request, "paymentType")) {
            String normalized = tryNormalizePaymentType(candidate);
            if (normalized != null) {
                return normalized;
            }
        }
        return "CREDITCARD";
    }

    String resolveAmount(HttpServletRequest request) {
        for (String candidate : collectParameterCandidates(request, "amount")) {
            if (isParseableAmount(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private List<String> collectParameterCandidates(HttpServletRequest request, String name) {
        List<String> candidates = new ArrayList<>();
        String[] values = request.getParameterValues(name);
        if (values == null) {
            return candidates;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            candidates.add(value.trim());
        }
        return candidates;
    }

    private boolean isParseableAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return false;
        }
        try {
            BigDecimal parsed = new BigDecimal(normalizeAmountInput(amount));
            return parsed.compareTo(BigDecimal.ZERO) > 0;
        } catch (NumberFormatException | ArithmeticException exception) {
            return false;
        }
    }

    private String tryNormalizePaymentType(String paymentType) {
        if (paymentType == null || paymentType.isBlank()) {
            return null;
        }
        try {
            return normalizePaymentType(paymentType);
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    public BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        String normalized = normalizeAmountInput(amount);
        try {
            BigDecimal parsed = new BigDecimal(normalized);
            if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Geçersiz amount değeri. Örnek: 250 veya 250.00"
                );
            }
            return formatOdealAmount(parsed);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Geçersiz amount değeri. Örnek: 250 veya 250.00"
            );
        }
    }

    /**
     * Ödeal D2D sepet API'si tutarları decimal (ör. 250 veya 250.00) bekler.
     * @see <a href="https://docs.odeal.com/entegrasyon/tr/api/d2d/nakit-sepet-aktar">Sepet Gönder - SIMPLE</a>
     */
    BigDecimal formatOdealAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeAmountInput(String amount) {
        String value = amount.trim()
                .replace("₺", "")
                .replace("TL", "")
                .replace("tl", "")
                .replace("TRY", "")
                .replace("try", "")
                .replace(" ", "");

        int lastComma = value.lastIndexOf(',');
        int lastDot = value.lastIndexOf('.');

        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) {
                value = value.replace(".", "").replace(",", ".");
            } else {
                value = value.replace(",", "");
            }
        } else if (lastComma >= 0) {
            value = value.replace(",", ".");
        }

        return value;
    }

    public JsonNode buildSampleBasket(String paymentType, BigDecimal amount) {
        String normalizedPaymentType = normalizePaymentType(paymentType);
        BigDecimal resolvedAmount = amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                ? formatOdealAmount(amount)
                : formatOdealAmount(new BigDecimal("250.00"));

        BigDecimal latteUnit = new BigDecimal("80.00");
        BigDecimal cheesecakeUnit = new BigDecimal("90.00");
        int latteQty = 2;
        int cheesecakeQty = 1;
        BigDecimal computedTotal = formatOdealAmount(
                latteUnit.multiply(BigDecimal.valueOf(latteQty))
                        .add(cheesecakeUnit.multiply(BigDecimal.valueOf(cheesecakeQty)))
        );

        if (computedTotal.compareTo(resolvedAmount) != 0) {
            cheesecakeQty = 1;
            latteQty = 1;
            latteUnit = resolvedAmount;
            cheesecakeUnit = BigDecimal.ZERO;
            computedTotal = resolvedAmount;
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("referenceCode", "test-" + UUID.randomUUID());
        if (properties.getExternalDeviceKey() != null && !properties.getExternalDeviceKey().isBlank()) {
            root.put("externalDeviceKey", properties.getExternalDeviceKey());
        }
        root.put("basketType", "SIMPLE");

        ObjectNode receiptInfo = objectMapper.createObjectNode();
        receiptInfo.put("Masa No", "12");
        receiptInfo.put("Adisyon No", "12345");
        receiptInfo.put("Garson", "Test Garson");
        root.set("receiptInfo", receiptInfo);

        ObjectNode customer = objectMapper.createObjectNode();
        customer.put("referenceCode", "guest-001");
        customer.put("type", "INDIVIDUAL");
        customer.put("name", "Misafir");
        customer.put("surname", "Musteri");
        customer.put("identityNumber", "11111111111");
        customer.put("gsmNumber", "5555555555");
        customer.put("email", "misafir@example.com");
        customer.put("city", "Istanbul");
        customer.put("town", "Kadikoy");
        customer.put("address", "Restoran");
        root.set("customer", customer);

        putOdealAmount(root, "price", computedTotal);
        putOdealAmount(root, "grossPrice", computedTotal);

        ArrayNode items = objectMapper.createArrayNode();
        items.add(buildItem("prod-latte", "Latte", latteQty, latteUnit));
        if (cheesecakeQty > 0 && cheesecakeUnit.compareTo(BigDecimal.ZERO) > 0) {
            items.add(buildItem("prod-cheesecake", "Cheesecake", cheesecakeQty, cheesecakeUnit));
        }
        root.set("items", items);

        ArrayNode paymentOptions = objectMapper.createArrayNode();
        ObjectNode paymentOption = objectMapper.createObjectNode();
        paymentOption.put("type", normalizedPaymentType);
        putOdealAmount(paymentOption, "amount", computedTotal);
        paymentOptions.add(paymentOption);
        root.set("paymentOptions", paymentOptions);

        return root;
    }

    private ObjectNode buildItem(String referenceCode, String name, int quantity, BigDecimal unitPrice) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("quantity", quantity);

        ObjectNode product = objectMapper.createObjectNode();
        product.put("referenceCode", referenceCode);
        product.put("name", name);
        product.put("unitCode", "C62");

        ObjectNode price = objectMapper.createObjectNode();
        putOdealAmount(price, "grossPrice", unitPrice);
        price.put("vatRatio", properties.getDefaultVatRatio());
        price.put("sctRatio", 0);
        product.set("price", price);

        item.set("product", product);
        return item;
    }

    private void putOdealAmount(ObjectNode node, String field, BigDecimal amount) {
        node.put(field, formatOdealAmount(amount));
    }

    private String normalizePaymentType(String paymentType) {
        if (paymentType == null || paymentType.isBlank()) {
            return "CREDITCARD";
        }
        return switch (paymentType.trim().toUpperCase(Locale.ROOT)) {
            case "CARD", "CREDITCARD", "CREDIT_CARD" -> "CREDITCARD";
            case "CASH", "NAKIT" -> "CASH";
            case "MONEY_TRANSFER", "HAVALE", "EFT" -> "MONEY_TRANSFER";
            case "OPEN_ACCOUNT", "ACIK_HESAP" -> "OPEN_ACCOUNT";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Geçersiz paymentType: " + paymentType
            );
        };
    }
}
