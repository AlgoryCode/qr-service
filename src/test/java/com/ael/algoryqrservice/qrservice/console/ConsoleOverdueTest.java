package com.ael.algoryqrservice.qrservice.console;

import com.ael.algoryqrservice.qrservice.console.domain.ConsoleOverdue;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleOverdueTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 23, 0);

    @Test
    void planned_isOverdueOnlyAfterTheGrace() {
        assertThat(ConsoleOverdue.check(60, 70 * 60, null, NOW)).isNull();
        assertThat(ConsoleOverdue.check(60, 71 * 60, null, NOW)).isEqualTo(ConsoleOverdue.Reason.PLANNED_EXCEEDED);
        assertThat(ConsoleOverdue.overMinutes(ConsoleOverdue.Reason.PLANNED_EXCEEDED, 60, 71 * 60, null, NOW)).isEqualTo(11);
    }

    @Test
    void openEnded_isOverdueAfterThreeHours() {
        assertThat(ConsoleOverdue.check(null, 180 * 60, null, NOW)).isNull();
        assertThat(ConsoleOverdue.check(null, 181 * 60, null, NOW)).isEqualTo(ConsoleOverdue.Reason.OPEN_TOO_LONG);
    }

    @Test
    void paused_isOverdueAfterAnHour() {
        assertThat(ConsoleOverdue.check(120, 30 * 60, NOW.minusMinutes(59), NOW)).isNull();
        assertThat(ConsoleOverdue.check(120, 30 * 60, NOW.minusMinutes(60), NOW)).isEqualTo(ConsoleOverdue.Reason.PAUSED_TOO_LONG);
        assertThat(ConsoleOverdue.overMinutes(ConsoleOverdue.Reason.PAUSED_TOO_LONG, 120, 1800, NOW.minusMinutes(75), NOW)).isEqualTo(75);
    }
}
