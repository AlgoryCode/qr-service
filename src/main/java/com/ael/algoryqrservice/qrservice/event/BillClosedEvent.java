package com.ael.algoryqrservice.qrservice.event;

public record BillClosedEvent(
        Long billId,
        Long menuId,
        Long closedByStaffId
) {
}
