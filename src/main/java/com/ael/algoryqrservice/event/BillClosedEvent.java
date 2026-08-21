package com.ael.algoryqrservice.event;

public record BillClosedEvent(
        Long billId,
        Long menuId,
        Long closedByWaiterId
) {
}
