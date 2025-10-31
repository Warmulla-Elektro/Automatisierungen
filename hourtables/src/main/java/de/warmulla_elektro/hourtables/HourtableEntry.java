package de.warmulla_elektro.hourtables;

import lombok.Value;
import org.comroid.annotations.Convert;
import org.comroid.api.net.nextcloud.model.tables.TableEntry;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Value
public class HourtableEntry {
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
    LocalTime startTime;
    LocalTime endTime;
    @Nullable Byte      breakMultiplier;
    @Nullable LocalTime breakStart;
    @Nullable String    details;
    @Nullable Boolean   vacation;

    public LocalDateTime getStart() {
        return LocalDateTime.of(date, startTime);
    }
}
