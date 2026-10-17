package com.ael.algoryqrservice.fulfillmentservice.domain.port;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.CatalogProduct;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ProductPort {

    Optional<CatalogProduct> findByCode(String code);

    Map<Long, CatalogProduct> findByIds(Collection<Long> ids);
}
