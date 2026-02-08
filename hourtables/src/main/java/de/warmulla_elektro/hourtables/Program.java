package de.warmulla_elektro.hourtables;

import lombok.extern.java.Log;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.comroid.api.io.FileHandle;
import org.comroid.api.model.Authentication;
import org.comroid.api.net.nextcloud.OcsApiWrapper;
import org.comroid.api.net.nextcloud.component.FilesApi;
import org.comroid.api.net.nextcloud.component.TablesApi;
import org.comroid.api.net.nextcloud.component.UserApi;
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument;
import org.odftoolkit.odfdom.dom.attribute.fo.FoPageHeightAttribute;
import org.odftoolkit.odfdom.dom.attribute.fo.FoPageWidthAttribute;
import org.odftoolkit.odfdom.dom.attribute.style.StyleNumFormatAttribute;
import org.odftoolkit.odfdom.dom.attribute.style.StylePrintOrientationAttribute;
import org.odftoolkit.odfdom.dom.style.OdfStyleFamily;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Log
public class Program {
    public static final String            OUT_DIR             = "./.out/";
    public static final DateTimeFormatter DATE                = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME       = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter SHEET_DATE = DateTimeFormatter.ofPattern("EE dd.MM.", Locale.GERMAN);
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
        /*
        try (var vert = OdfSpreadsheetDocument.loadDocument(new File("vertikal.ods"));
             var hori = OdfSpreadsheetDocument.loadDocument(new File("horizontal.ods"))) {

            System.out.println(Debug.createObjectDump(vert));
            System.out.println("#######################################");
            System.out.println(Debug.createObjectDump(hori));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
         */

        CommandLine args;
        try {
            args = new DefaultParser().parse(OPTIONS, $args);
        } catch (ParseException e) {
            throw new RuntimeException("Unable to parse command line arguments", e);
        }

        var outDir = new FileHandle(OUT_DIR);
        if (!outDir.mkdirs()) throw new RuntimeException("Unable to create output directory");

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
                        final var $year = year;
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

                if (useData.isEmpty()) {
                    log.warning("No entries for user %s, skipping".formatted(user));
                    continue;
                }
                log.info("Found %d entries for user %s".formatted(useData.size(), user));

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

                    if (weekData.isEmpty()) {
                        log.warning("No entries for user %s, skipping".formatted(user));
                        continue;
                    }
                    log.info("Found %d week entries for user %s".formatted(useData.size(), user));

                    // tasks
                    // generate ODS file
                    var documentPath = new File(OUT_DIR).getAbsolutePath() + '/' + user + ".ods";
                    log.info("Generating ODS for %s at path '%s'".formatted(user, documentPath));

                    try (var ods = OdfSpreadsheetDocument.newSpreadsheetDocument()) {
                        var dom = ods.getStylesDom();
                        var attributes = dom.getElementsByTagName("style:page-layout-properties")
                                .item(0)
                                .getAttributes();
                        attributes.setNamedItem(new FoPageWidthAttribute(dom) {{setNodeValue("297mm");}});
                        attributes.setNamedItem(new FoPageHeightAttribute(dom) {{setNodeValue("210.01mm");}});
                        attributes.setNamedItem(new StyleNumFormatAttribute(dom) {{setNodeValue("1");}});
                        attributes.setNamedItem(new StylePrintOrientationAttribute(dom) {{setNodeValue("landscape");}});

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

                        int tableRow = 7;
                        nextDay:
                        for (var dayEntries : new TreeMap<>(weekData.stream()
                                .collect(Collectors.groupingBy(HourtableEntry::getDate))).entrySet()) {
                            long dayDuration = 0;
                            table.getCellByPosition(0, tableRow).setStringValue(SHEET_DATE.format(dayEntries.getKey()));

                            for (var entry : dayEntries.getValue()) {
                                table.getCellByPosition(2, tableRow).setStringValue(entry.getCustomer());

                                // vacation?
                                if (Boolean.TRUE.equals(entry.getVacation())) {
                                    tableRow += 2;
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
                                    start = entry.getBreakStart()
                                            .plus(Duration.ofMinutes(15L * entry.getBreakMultiplier()));
                                    end           = entry.getEndTime();
                                    entryDuration = start.until(end, ChronoUnit.NANOS);
                                    dayDuration += entryDuration;
                                    table.getCellByPosition(3, tableRow).setStringValue(TIME.format(start));
                                }

                                table.getCellByPosition(4, tableRow).setStringValue(TIME.format(end));
                                table.getCellByPosition(5, tableRow)
                                        .setStringValue(TIME.format(LocalTime.ofNanoOfDay(entryDuration)));

                                if (args.hasOption('o')) System.out.println(entry);

                                var details = entry.getDetails();
                                if (details.toLowerCase()
                                        .contains("bitte angeben")) details = "(keine Details angegeben)";
                                table.getCellByPosition(8, tableRow).setStringValue(details);
                                tableRow += 1;
                            }

                            tableRow -= 1;

                            // print day conclusion
                            weekDuration += dayDuration;
                            table.getCellByPosition(6, tableRow)
                                    .setStringValue(TIME.format(LocalTime.ofNanoOfDay(dayDuration)));
                            table.getCellByPosition(7, tableRow)
                                    .setDoubleValue((double) TimeUnit.NANOSECONDS.toMinutes(dayDuration) / 60);

                            tableRow += 2;
                        }

                        table.getCellByPosition(6, tableRow).setStringValue("Woche Gesamt:");
                        table.getCellByPosition(7, tableRow)
                                .setDoubleValue((double) TimeUnit.NANOSECONDS.toMinutes(weekDuration) / 60);

                        // col sizes
                        table.getColumnByIndex(0).setWidth(22);
                        table.getColumnByIndex(1).setWidth(13);
                        table.getColumnByIndex(2).setWidth(40);
                        table.getColumnByIndex(3).setWidth(14);
                        table.getColumnByIndex(4).setWidth(14);
                        table.getColumnByIndex(5).setWidth(14);
                        table.getColumnByIndex(6).setWidth(30);
                        table.getColumnByIndex(7).setWidth(15);
                        table.getColumnByIndex(8).setWidth(90);

                        // borders
                        final var styleBorder = "borders";
                        var tableCellStyle = ods.getOrCreateDocumentStyles()
                                .newStyle(styleBorder, OdfStyleFamily.TableCell);
                        var cellStyleProp = tableCellStyle.newStyleTableCellPropertiesElement();
                        cellStyleProp.setFoBorderAttribute("0.05pt solid #000000");

                        for (var row = 4; row <= tableRow - 2; row++)
                            for (var col = 0; col < 9; col++)
                                table.getCellByPosition(col, row).getOdfElement().setStyleName(styleBorder);

                        ods.save(documentPath);
                    } catch (Exception e) {
                        log.log(Level.SEVERE, "Could not generate ODS file for " + user, e);
                        continue;
                    }

                    // upload file
                    if (!args.hasOption('u')) {
                        log.info("No upload task specified");
                        continue;
                    }
                    log.info("Uploading ODS for " + user);
                    var files    = ocs.child(FilesApi.class).assertion();
                    var tableDir = "Stunden " + year + "/Kalenderwoche " + week;
                    try {
                        files.mkdirs(tableDir).join();
                    } catch (Throwable t) {
                        log.log(Level.WARNING, "Could not MKDIR", t);
                    }
                    try (var fis = new FileInputStream(documentPath)) {
                        files.upload(tableDir + '/' + user + ".ods", fis);
                    } catch (IOException e) {
                        log.log(Level.SEVERE, "Could not upload ODS file for " + user, e);
                    }
                }
            }
        }
    }

    private static int week(ChronoLocalDate date) {
        return date.get(WeekFields.of(Locale.GERMAN).weekOfWeekBasedYear());
    }
}
