package com.ael.algoryqrservice.purchase.lifecycle;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;

import java.util.Optional;

public final class UserPackageLifecyclePolicy {

    private UserPackageLifecyclePolicy() {
    }

    public static boolean canDeactivate(Optional<Purchase> activePackage) {
        return activePackage.isPresent();
    }

    public static boolean canReactivate(Optional<Purchase> expiredPackage) {
        return expiredPackage
                .filter(purchase -> purchase.getStatus() == PurchaseStatus.EXPIRED)
                .isPresent();
    }
}
