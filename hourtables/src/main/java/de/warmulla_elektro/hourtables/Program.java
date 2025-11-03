package de.warmulla_elektro.hourtables;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.comroid.api.func.ext.Context;
import org.comroid.api.io.FileHandle;
import org.comroid.api.model.Authentication;
import org.comroid.api.net.nextcloud.OcsApiWrapper;
import org.comroid.api.net.nextcloud.component.TablesApi;
import org.comroid.api.net.nextcloud.component.UserApi;
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Program {
    public static final String            OUT_DIR             = "./.out/";
    public static final DateTimeFormatter DATE                = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter SHEET_DATE          = DateTimeFormatter.ofPattern("EE dd.MM.");
    public static final FileHandle        NC_API_BOT_PASSWORD = new FileHandle("nc_api_bot_password.cred");
    public static final Options           OPTIONS             = new Options() {{
        addOption(new Option("users", true, "Space separated list of users"));
        addOption(new Option("year", true, "Calendar: Year selector"));
        addOption(new Option("month", true, "Calendar: Month selector"));
        addOption(new Option("week", true, "Calendar: Week selector"));
        addOption(new Option("since", true, "Calendar: Start date selector (yyyy-MM-dd)"));
        addOption(new Option("customer", true, "Customer selector"));
        addOption(new Option("detail", true, "Detail selector"));
        addOption(new Option("o", false, "Generate ODS file"));
        addOption(new Option("u", false, "Upload File; implies -o"));
        //addOption(new Option("t", false, "Calculate and publish overtime data; implies -c"));
        addOption(new Option("p", false, "Print everything to STDOUT as well as to file"));
        //addOption(new Option("v", false, "Verbose Logging"));
    }};

    public static void main(String... $args) {
        Context.Base.ROOT.getMyMembers().add(new ObjectMapper(new JsonFactoryBuilder() {{
            enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION);
        }}.build()) {
            {
                registerModule(new JavaTimeModule());
            }
        });

        CommandLine args;
        try {
            args = new DefaultParser().parse(OPTIONS, $args);
        } catch (ParseException e) {
            throw new RuntimeException("Unable to parse command line arguments", e);
        }

        try (
                var ocs = OcsApiWrapper.builder()
                        .baseUrl("https://warmulla.kaleidox.de")
                        .credentials(Authentication.ofLogin("bot", NC_API_BOT_PASSWORD.getContent(false)))
                        .build()
        ) {
            final var users = args.hasOption("users")
                              ? args.getOptionValues("users")
                              : ocs.child(UserApi.class).assertion().getUsers().join().toArray(String[]::new);
            final var data = ocs.child(TablesApi.class)
                    .assertion()
                    .getEntries(2)
                    .join()
                    .stream()
                    .map(HourtableEntry::convert)
                    .sorted()
                    .toList();

            for (var user : users) {
                var stream = data.stream();

                // user selector
                stream = stream.filter(entry -> entry.getUser().equals(user));

                // customer selector
                if (args.hasOption("customer")) {
                    final var customer = args.getOptionValue("customer");
                    stream = stream.filter(entry -> entry.getCustomer().matches(customer));
                }

                // detail selector
                if (args.hasOption("detail")) {
                    final var detail = args.getOptionValue("detail");
                    stream = stream.filter(entry -> {
                        var details = entry.getDetails();
                        return details != null && details.matches(detail);
                    });
                }

                // calendar selector
                LocalDate since = null;
                int       year  = LocalDate.now().getYear();
                if (args.hasOption("since")) {
                    since = LocalDate.from(DATE.parse(args.getOptionValue("since")));
                    final var lowerBound = LocalDateTime.of(since, LocalTime.of(0, 0, 0));
                    stream = stream.filter(entry -> entry.getStart().isAfter(lowerBound));
                } else {
                    if (args.hasOption("year")) {
                        final var $year = year = Integer.parseInt(args.getOptionValue("year"));
                        stream = stream.filter(entry -> entry.getDate().getYear() == $year);
                    } else {
                        int $year = year;
                        stream = stream.filter(entry -> entry.getDate().getYear() == $year);
                    }

                    if (args.hasOption("month")) {
                        final var month = Integer.parseInt(args.getOptionValue("month"));
                        stream = stream.filter(entry -> entry.getDate().getMonthValue() == month);
                    } else if (args.hasOption("week")) {
                        final var week = Integer.parseInt(args.getOptionValue("week"));
                        stream = stream.filter(entry -> week(entry.getDate()) == week);
                    }
                }

                final var useData = stream.toList();

                if (useData.isEmpty()) continue;

                // foreach week
                final int[] weeks;
                if (args.hasOption("week")) weeks = new int[]{ Integer.parseInt(args.getOptionValue("week")) };
                else {
                    var currentWeek = week(LocalDate.now());
                    weeks = since != null
                            ? IntStream.rangeClosed(week(since), currentWeek).toArray()
                            : new int[]{ currentWeek };
                }

                for (var week : weeks) {
                    long      weekDuration = 0;
                    final var weekData     = useData.stream().filter(entry -> week(entry.getDate()) == week).toList();

                    if (weekData.isEmpty()) continue;

                    // tasks
                    // generate ODS file
                    try (var ods = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
                        var table = ods.getTableByName("Sheet1");

                        table.getCellByPosition("A5").setStringValue(user);
                        table.getCellByPosition("B5").setStringValue("Rg. Nr.");
                        table.getCellByPosition("C5").setStringValue(year + " Woche " + week);
                        table.getCellByPosition("D5").setStringValue("Von");
                        table.getCellByPosition("E5").setStringValue("Bis");
                        table.getCellByPosition("F5").setStringValue("Gesamt");
                        table.getCellByPosition("G5").setStringValue("Tag Gesamt");
                        table.getCellByPosition("H5").setStringValue("Dezimal");
                        table.getCellByPosition("I5").setStringValue("Anmerkungen");

                        int tableRow = 8;
                        nextDay:
                        for (var dayEntries : weekData.stream()
                                .collect(Collectors.groupingBy(HourtableEntry::getDate))
                                .entrySet()) {
                            long dayDuration = 0;
                            table.getCellByPosition(0, tableRow).setStringValue(SHEET_DATE.format(dayEntries.getKey()));

                            for (var entry : dayEntries.getValue()) {
                                table.getCellByPosition(2, tableRow).setStringValue(entry.getCustomer());

                                // vacation?
                                if (Boolean.TRUE.equals(entry.getVacation())) {
                                    continue nextDay;
                                }

                                // print client start
                                var start = entry.getStartTime();
                                table.getCellByPosition(3, tableRow).setStringValue(TIME.format(start));
                                long entryDuration;

                                LocalTime end;
                                if (entry.getBreakStart() == null || entry.getBreakMultiplier() == null || entry.getBreakMultiplier() == 0) {
                                    // single line entry
                                    end           = entry.getEndTime();
                                    entryDuration = start.until(end, ChronoUnit.NANOS);
                                    dayDuration += entryDuration;
                                } else {
                                    // separated entry
                                    end           = entry.getBreakStart();
                                    entryDuration = start.until(end, ChronoUnit.NANOS);
                                    dayDuration += entryDuration;
                                    table.getCellByPosition(4, tableRow)
                                            .setStringValue(TIME.format(entry.getBreakStart()));
                                    table.getCellByPosition(5, tableRow)
                                            .setStringValue(TIME.format(LocalTime.ofNanoOfDay(entryDuration)));

                                    // row after break
                                    tableRow++;
                                    end           = entry.getEndTime();
                                    entryDuration = start.until(end, ChronoUnit.NANOS);
                                    dayDuration += entryDuration;
                                    table.getCellByPosition(3, tableRow)
                                            .setStringValue(TIME.format(entry.getBreakStart()
                                                    .plus(Duration.ofMinutes(15 + Objects.requireNonNull(entry.getBreakMultiplier(),
                                                            "breakMultiplier")))));
                                }
                                table.getCellByPosition(4, tableRow).setStringValue(TIME.format(end));
                                table.getCellByPosition(5, tableRow)
                                        .setStringValue(TIME.format(LocalTime.ofNanoOfDay(entryDuration)));

                                // also print day conclusion
                                weekDuration += dayDuration;
                                table.getCellByPosition(6, tableRow)
                                        .setStringValue(TIME.format(LocalTime.ofNanoOfDay(dayDuration)));
                                table.getCellByPosition(7, tableRow)
                                        .setDoubleValue((double) TimeUnit.NANOSECONDS.toMinutes(dayDuration) / 60);
                                table.getCellByPosition(8, tableRow).setStringValue(entry.getDetails());

                                tableRow += 2;
                            }
                        }

                        tableRow += 2;
                        table.getCellByPosition(6, tableRow).setStringValue("Woche Gesamt:");
                        table.getCellByPosition(7, tableRow)
                                .setDoubleValue((double) TimeUnit.NANOSECONDS.toMinutes(weekDuration) / 60);

                        ods.save(OUT_DIR + user + ".ods");
                    } catch (Exception e) {
                        throw new RuntimeException("Could not generate ODS file", e);
                    }

                    // upload file
                }
            }
        }
    }

    private static int week(ChronoLocalDate date) {
        return date.get(WeekFields.of(Locale.GERMAN).weekOfWeekBasedYear());
    }
}
