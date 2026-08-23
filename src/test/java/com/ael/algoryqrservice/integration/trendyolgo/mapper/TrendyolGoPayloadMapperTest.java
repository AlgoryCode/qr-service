package com.ael.algoryqrservice.integration.trendyolgo.mapper;

import com.ael.algoryqrservice.integration.trendyolgo.model.dto.TrendyolGoDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendyolGoPayloadMapperTest {

    private final TrendyolGoPayloadMapper mapper = new TrendyolGoPayloadMapper();
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

        List<TrendyolGoDtos.RestaurantResponse> restaurants = mapper.toRestaurants(root);

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

        List<TrendyolGoDtos.ProductResponse> products = mapper.toProducts(root);

        assertThat(products).hasSize(1);
        assertThat(products.getFirst().getName()).isEqualTo("Cheeseburger");
        assertThat(products.getFirst().getCategoryName()).isEqualTo("Burger");
        assertThat(products.getFirst().getPrice()).isEqualByComparingTo("220");
        assertThat(products.getFirst().isAvailable()).isTrue();
    }

    @Test
    void toOrderNodes_whenWebhookPackage_thenExtractFields() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "id": "ord-9",
                  "restaurantId": "r-1",
                  "packageStatus": "Created",
                  "totalPrice": 150.50,
                  "customer": { "firstName": "Ayşe", "lastName": "Yılmaz", "phone": "0555" },
                  "deliveryAddress": { "address1": "Bağdat Cd.", "district": "Kadıköy" },
                  "lines": [
                    { "productId": "p-1", "productName": "Ayran", "quantity": 2, "price": 20 }
                  ]
                }
                """);

        assertThat(mapper.externalOrderId(root)).isEqualTo("ord-9");
        assertThat(mapper.restaurantId(root)).isEqualTo("r-1");
        assertThat(mapper.packageStatus(root)).isEqualTo("Created");
        assertThat(mapper.totalAmount(root)).isEqualByComparingTo(new BigDecimal("150.50"));
        assertThat(mapper.customerName(root)).isEqualTo("Ayşe Yılmaz");
        assertThat(mapper.deliveryAddress(root)).contains("Bağdat Cd.");
        assertThat(mapper.toOrderItems(root)).hasSize(1);
        assertThat(mapper.toOrderNodes(root)).hasSize(1);
    }
}
