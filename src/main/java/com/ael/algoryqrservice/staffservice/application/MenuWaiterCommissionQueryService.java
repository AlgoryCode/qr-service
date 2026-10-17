package com.ael.algoryqrservice.staffservice.application;

import com.ael.algoryqrservice.qrservice.order.dto.TableBillDtos;
import com.ael.algoryqrservice.qrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.qrservice.repository.TableBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ael.algoryqrservice.staffservice.application.StaffCommissionService;

@Service
@RequiredArgsConstructor
public class MenuWaiterCommissionQueryService {

    private final StaffCommissionService waiterCommissionService;
    private final TableBillRepository tableBillRepository;
    private final RestaurantTableRepository restaurantTableRepository;

    @Transactional(readOnly = true)
    public TableBillDtos.TodayCommissionSummary getTodaySummary(Long staffId) {
        TableBillDtos.TodayCommissionSummary summary = waiterCommissionService.getTodaySummary(staffId);
        return summary.toBuilder()
                .records(enrichRecords(summary.getRecords()))
                .build();
    }

    @Transactional(readOnly = true)
    public TableBillDtos.CommissionHistoryResponse getHistory(
            Long staffId,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        TableBillDtos.CommissionHistoryResponse history =
                waiterCommissionService.getHistory(staffId, from, to, page, size);
        return TableBillDtos.CommissionHistoryResponse.builder()
                .records(enrichRecords(history.getRecords()))
                .page(history.getPage())
                .size(history.getSize())
                .totalElements(history.getTotalElements())
                .totalPages(history.getTotalPages())
                .build();
    }

    private List<TableBillDtos.CommissionRecordResponse> enrichRecords(
            List<TableBillDtos.CommissionRecordResponse> records
    ) {
        if (records == null || records.isEmpty()) {
            return records;
        }

        Map<Long, String> tableNamesByBillId = new HashMap<>();
        return records.stream()
                .map(record -> {
                    String tableName = null;
                    if (record.getBillId() != null) {
                        tableName = tableNamesByBillId.computeIfAbsent(record.getBillId(), billId ->
                                tableBillRepository.findById(billId)
                                        .flatMap(bill -> restaurantTableRepository.findById(bill.getTableId()))
                                        .map(table -> table.getName())
                                        .orElse(null)
                        );
                    }
                    return record.toBuilder().tableName(tableName).build();
                })
                .toList();
    }
}
