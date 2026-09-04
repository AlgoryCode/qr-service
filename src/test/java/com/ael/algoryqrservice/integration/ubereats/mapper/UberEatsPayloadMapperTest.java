package com.ael.algoryqrservice.integration.ubereats.mapper;

import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UberEatsPayloadMapperTest {

    private final UberEatsPayloadMapper mapper = new UberEatsPayloadMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toRestaurants_whenStoresArray_thenMapIdAndName() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "stores": [
                    { "id": "r-1", "name": "Kadıköy", "address": "Moda Cd." }
                  ]
                }
                """);

        List<UberEatsDtos.RestaurantResponse> restaurants = mapper.toRestaurants(root);

        assertThat(restaurants).hasSize(1);
        assertThat(restaurants.getFirst().getId()).isEqualTo("r-1");
        assertThat(restaurants.getFirst().getName()).isEqualTo("Kadıköy");
    }

    @Test
    void toProducts_whenCategories_thenFlatten() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "categories": [
                    {
                      "name": "Burger",
                      "products": [
                        { "id": "p-1", "name": "Cheeseburger", "price": 220, "selling": true }
                      ]
                    }
                  ]
                }
                """);

        List<UberEatsDtos.ProductResponse> products = mapper.toProducts(root);

        assertThat(products).hasSize(1);
        assertThat(products.getFirst().getName()).isEqualTo("Cheeseburger");
        assertThat(products.getFirst().getCategoryName()).isEqualTo("Burger");
        assertThat(products.getFirst().getPrice()).isEqualByComparingTo("220");
        assertThat(products.getFirst().isAvailable()).isTrue();
    }

    @Test
    void toProducts_whenCategoryStubAndCatalog_thenEnrichNameFromCatalog() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "categories": [
                    {
                      "name": "İçecekler",
                      "products": [
                        { "id": "8722497" }
                      ]
                    }
                  ],
                  "products": [
                    {
                      "id": "8722497",
                      "name": "Ayran",
                      "description": "Ev yapımı",
                      "price": 40,
                      "selling": true
                    }
                  ]
                }
                """);

        List<UberEatsDtos.ProductResponse> products = mapper.toProducts(root);

        assertThat(products).hasSize(1);
        assertThat(products.getFirst().getId()).isEqualTo("8722497");
        assertThat(products.getFirst().getName()).isEqualTo("Ayran");
        assertThat(products.getFirst().getDescription()).isEqualTo("Ev yapımı");
        assertThat(products.getFirst().getCategoryName()).isEqualTo("İçecekler");
        assertThat(products.getFirst().getPrice()).isEqualByComparingTo("40");
    }

    @Test
    void toOrderNodes_whenWebhookPackage_thenExtractFields() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "id": "ord-9",
                  "orderCode": "TGO-12345",
                  "restaurantId": "r-1",
                  "packageStatus": "Created",
                  "deliveryType": "STORE",
                  "paymentMethodText": "Online Ödeme",
                  "totalPrice": 150.50,
                  "customer": { "firstName": "Ayşe", "lastName": "Yılmaz", "phone": "0555" },
                  "address": {
                    "neighborhood": "Kadıköy",
                    "address1": "Bağdat Cd.",
                    "phone": "05551234567"
                  },
                  "customerNote": "Acısız",
                  "lines": [
                    {
                      "productId": "p-1",
                      "name": "Ayran",
                      "price": 20,
                      "unitSellingPrice": 25,
                      "items": [{}, {}],
                      "extraIngredients": [{ "name": "Limon" }],
                      "removedIngredients": [{ "name": "Soğan" }]
                    }
                  ]
                }
                """);

        assertThat(mapper.externalOrderId(root)).isEqualTo("ord-9");
        assertThat(mapper.orderNumber(root)).isEqualTo("TGO-12345");
        assertThat(mapper.deliveryType(root)).isEqualTo("STORE");
        assertThat(mapper.paymentMethod(root)).isEqualTo("Online Ödeme");
        assertThat(mapper.restaurantId(root)).isEqualTo("r-1");
        assertThat(mapper.packageStatus(root)).isEqualTo("Created");
        assertThat(mapper.totalAmount(root)).isEqualByComparingTo(new BigDecimal("150.50"));
        assertThat(mapper.customerName(root)).isEqualTo("Ayşe Yılmaz");
        assertThat(mapper.customerPhone(root)).isEqualTo("0555");
        assertThat(mapper.deliveryAddress(root)).contains("Bağdat Cd.");
        assertThat(mapper.note(root)).isEqualTo("Acısız");
        assertThat(mapper.toOrderItems(root)).hasSize(1);
        assertThat(mapper.toOrderItems(root).getFirst().getProductName()).isEqualTo("Ayran");
        assertThat(mapper.toOrderItems(root).getFirst().getQuantity()).isEqualTo(2);
        assertThat(mapper.toOrderItems(root).getFirst().getDetail()).contains("Limon");
        assertThat(mapper.toOrderNodes(root)).hasSize(1);
    }
}
