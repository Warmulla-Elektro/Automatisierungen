package de.warmulla_elektro.hourtables;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.comroid.api.attr.IntegerAttribute;
import org.comroid.api.net.nextcloud.model.tables.ColumnValue;
import org.comroid.api.net.nextcloud.model.tables.TableEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public enum TableColumn implements IntegerAttribute {
    DATE(7) {
        @Override
        public @NotNull LocalDate extract(TableEntry entry) {
            return LocalDate.from(Program.DATE.parse(Objects.requireNonNull(super.extract(entry), "date").toString()));
        }
    }, CUSTOMER(8) {
        @Override
        public @NotNull String extract(TableEntry entry) {
            return Objects.requireNonNull(super.extract(entry), "customer").toString();
        }
    }, START_TIME(9) {
        @Override
        public @NotNull LocalTime extract(TableEntry entry) {
            return LocalTime.from(Program.TIME.parse(Objects.requireNonNull(super.extract(entry), "startTime")
                    .toString()));
        }
    }, ENDTIME(10) {
        @Override
        public @NotNull LocalTime extract(TableEntry entry) {
            return LocalTime.from(Program.TIME.parse(Objects.requireNonNull(super.extract(entry), "endTime")
                    .toString()));
        }
    }, BREAK_MULTIPLIER(13) {
        @Override
        public @NotNull Byte extract(TableEntry entry) {
            return Byte.parseByte(Objects.requireNonNull(super.extract(entry), "breakMultiplier").toString());
        }
    }, BREAK_START(22) {
        @Override
        public @Nullable LocalTime extract(TableEntry entry) {
            var extract = super.extract(entry);
            return extract == null ? null : LocalTime.from(Program.TIME.parse(extract.toString()));
        }
    }, DETAILS(23) {
        @Override
        public @Nullable String extract(TableEntry entry) {
            var extract = super.extract(entry);
            return extract == null ? null : extract.toString();
        }
    }, VACATION(75) {
        @Override
        public @NotNull Boolean extract(TableEntry entry) {
            var extract = super.extract(entry);
            return extract != null && Boolean.parseBoolean(extract.toString());
        }
    }/*,
    COLLEAGUES(76) {
        @Override
        public Object extract(TableEntry entry) {
            return null;
        }
    }*/;

    int value;

    @Override
    public @NotNull Integer getValue() {
        return value;
    }

    public @Nullable Object extract(TableEntry entry) {
        return entry.getData()
                .stream()
                .filter(cv -> cv.getColumnId() == value)
                .findAny()
                .map(ColumnValue::getValue)
                .orElse(null);
    }
}
