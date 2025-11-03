package de.warmulla_elektro.hourtables;

import lombok.Value;
import org.comroid.annotations.Convert;
import org.comroid.api.net.nextcloud.model.tables.TableEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;

@Value
public class HourtableEntry implements Comparable<HourtableEntry> {
    public static final Comparator<HourtableEntry> COMPARATOR = Comparator.comparing(HourtableEntry::getStart);

    @Convert
    public static HourtableEntry convert(TableEntry entry) {
        return new HourtableEntry(entry.getCreatedBy(),
                (LocalDate) TableColumn.DATE.extract(entry),
                (String) TableColumn.CUSTOMER.extract(entry),
                (LocalTime) TableColumn.START_TIME.extract(entry),
                (LocalTime) TableColumn.ENDTIME.extract(entry),
                (Byte) TableColumn.BREAK_MULTIPLIER.extract(entry),
                (LocalTime) TableColumn.BREAK_START.extract(entry),
                (String) TableColumn.DETAILS.extract(entry),
                (Boolean) TableColumn.VACATION.extract(entry));
    }

    String    user;
    LocalDate date;
    String    customer;
    @Nullable LocalTime startTime;
    @Nullable LocalTime endTime;
    @Nullable Byte      breakMultiplier;
    @Nullable LocalTime breakStart;
    @Nullable String    details;
    @Nullable Boolean   vacation;

    public LocalDateTime getStart() {
        return LocalDateTime.of(date, startTime == null ? LocalTime.MIDNIGHT : startTime);
    }

    @Override
    public int compareTo(@NotNull HourtableEntry other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return "[%s] (%s %s-%s) %s: %s (Pause: %s)".formatted(user,
                Program.DATE.format(date),
                startTime == null ? "" : Program.TIME.format(startTime),
                endTime == null ? "" : Program.TIME.format(endTime),
                customer,
                details,
                breakMultiplier == null || breakStart == null
                ? "keine"
                : "%dm um %s".formatted(breakMultiplier * 15, Program.TIME.format(breakStart)));
    }
}
