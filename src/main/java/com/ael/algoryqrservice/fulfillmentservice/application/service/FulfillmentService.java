package com.ael.algoryqrservice.fulfillmentservice.application.service;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackage;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ActivePackageView;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.FulfillmentCommand;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageFulfillment;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.UserEntitlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface FulfillmentService {

    PackageFulfillment fulfillPaidPackage(FulfillmentCommand command);

    void assignPackage(Long userId, String packageCode, int periodDays);

    Optional<ActivePackage> findActivePackage(Long userId);

    ActivePackage requireActivePackage(Long userId);

    ActivePackageView requireActivePackageView(Long userId);

    PackageFulfillment requireFulfillment(Long fulfillmentId);

    Page<PackageFulfillment> listFulfillments(Long userId, Pageable pageable);

    List<UserEntitlement> listActiveEntitlements(Long userId);
}
