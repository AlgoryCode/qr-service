package com.ael.algoryqrservice.purchase.lifecycle;

import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.model.enums.PurchaseStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserPackageLifecyclePolicyTest {

    @Test
    void canDeactivate_whenActivePresent() {
        assertThat(UserPackageLifecyclePolicy.canDeactivate(Optional.of(Purchase.builder().build()))).isTrue();
        assertThat(UserPackageLifecyclePolicy.canDeactivate(Optional.empty())).isFalse();
    }

    @Test
    void canReactivate_whenExpiredPresent() {
        Purchase expired = Purchase.builder().status(PurchaseStatus.EXPIRED).build();
        Purchase active = Purchase.builder().status(PurchaseStatus.ACTIVE).build();
        assertThat(UserPackageLifecyclePolicy.canReactivate(Optional.of(expired))).isTrue();
        assertThat(UserPackageLifecyclePolicy.canReactivate(Optional.of(active))).isFalse();
        assertThat(UserPackageLifecyclePolicy.canReactivate(Optional.empty())).isFalse();
    }
}
