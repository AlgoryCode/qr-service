package com.ael.algoryqrservice.store.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreWorkingHour {

    private DayOfWeek day;
    private LocalTime opensAt;
    private LocalTime closesAt;
    private boolean closed;
}
