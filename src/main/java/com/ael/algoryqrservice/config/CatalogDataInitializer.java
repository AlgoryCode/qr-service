package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.enums.ProductCode;
import com.ael.algoryqrservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogDataInitializer implements ApplicationRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (productRepository.existsByCode(ProductCode.QR_CREATE)) {
            return;
        }

        productRepository.save(Product.builder()
                .code(ProductCode.QR_CREATE)
                .name("QR Oluşturma Hakkı")
                .description("Kullanıcıya QR kod oluşturma hakkı tanır")
                .active(true)
                .build());

        log.info("Varsayılan ürün oluşturuldu: {}", ProductCode.QR_CREATE);
    }
}
