package gov.usgs.volcanoes.swarm.statistics;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.time.LocalDate;


public class CatalogStatisticsEngine {

	private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
		DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
		DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
		DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
	};

	public static List<CatalogEvent> readCatalogRange(
			File catalogRoot,
			LocalDateTime startUtc,
			LocalDateTime endUtc) throws IOException {

		List<CatalogEvent> allEvents = new ArrayList<CatalogEvent>();

		LocalDate date = startUtc.toLocalDate();
		LocalDate endDate = endUtc.toLocalDate();

		while (!date.isAfter(endDate)) {

			String year = String.format("%04d", date.getYear());

			String yyyymmdd = String.format(
				"%04d%02d%02d",
				date.getYear(),
				date.getMonthValue(),
				date.getDayOfMonth()
			);

		File dayFolder =
			new File(new File(catalogRoot, year), yyyymmdd);

		if (!dayFolder.exists() || !dayFolder.isDirectory()) {

//			System.out.println("[CatalogStatisticsEngine] Missing folder: "
//				+ dayFolder.getAbsolutePath());

			date = date.plusDays(1);
			continue;
		}

		File[] csvFiles = dayFolder.listFiles((dir, name) -> {

			String lower = name.toLowerCase();

			return lower.endsWith(".csv")
				&& lower.contains("catalog");
		});

		if (csvFiles == null || csvFiles.length == 0) {

			System.out.println("[CatalogStatisticsEngine] No catalog CSV found: "
				+ dayFolder.getAbsolutePath());

			date = date.plusDays(1);
			continue;
		}

		for (File catalogFile : csvFiles) {

			System.out.println("[CatalogStatisticsEngine] Reading: "
				+ catalogFile.getAbsolutePath());

		List<CatalogEvent> dayEvents = readCatalog(catalogFile);

		for (CatalogEvent event : dayEvents) {

			if (event.time == null) {
				continue;
			}

			if (event.time.isBefore(startUtc) || event.time.isAfter(endUtc)) {
				continue;
			}

			allEvents.add(event);
		}
		}

			date = date.plusDays(1);
		}

		return allEvents;
	}

    public static List<CatalogEvent> readCatalog(File file) throws IOException {

        List<CatalogEvent> events = new ArrayList<>();

        BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)
        );

        String headerLine = br.readLine();

        if (headerLine == null) {
            br.close();
            return events;
        }

        String delimiter = detectDelimiter(headerLine);
        String[] headers = splitLine(headerLine, delimiter);

        Map<String, Integer> col = mapHeader(headers);

        String line;

        while ((line = br.readLine()) != null) {

            if (line.trim().isEmpty()) {
                continue;
            }

            String[] p = splitLine(line, delimiter);

            try {
                LocalDateTime time = parseTime(get(p, col, "time"));

                String station = get(p, col, "station");
				String channel = get(p, col, "channel");
                String eventType = get(p, col, "type");

                double amplitude = parseDouble(get(p, col, "amplitude"));
                double duration = parseDouble(get(p, col, "duration"));

                events.add(new CatalogEvent(time, station, channel, eventType, amplitude, duration));

			} catch (Exception ex) {
				System.out.println("[CatalogStatisticsEngine] Failed parse line:");
				System.out.println(line);
				System.out.println("[Reason] " + ex.getMessage());
			}
        }

        br.close();

        return events;
    }

	public static LocalDateTime localToUtc(LocalDateTime localTime, String zoneLabel) {

		ZoneId localZone = getZoneId(zoneLabel);
		ZonedDateTime localZoned =
			localTime.atZone(localZone);
		ZonedDateTime utcZoned =
			localZoned.withZoneSameInstant(ZoneId.of("UTC"));
		return utcZoned.toLocalDateTime();
	}

	public static LocalDateTime utcToLocal(LocalDateTime utcTime, String zoneLabel) {

		ZoneId localZone = getZoneId(zoneLabel);
		ZonedDateTime utcZoned =
			utcTime.atZone(ZoneId.of("UTC"));
		ZonedDateTime localZoned =
			utcZoned.withZoneSameInstant(localZone);
		return localZoned.toLocalDateTime();
	}

	public static ZoneId getZoneId(String zoneLabel) {

		if (zoneLabel != null && zoneLabel.startsWith("WITA")) {
			return ZoneId.of("Asia/Makassar");
		}

		if (zoneLabel != null && zoneLabel.startsWith("WIT")) {
			return ZoneId.of("Asia/Jayapura");
		}

		return ZoneId.of("Asia/Jakarta"); // default WIB
	}

    public static Map<String, EventStatistics> calculate(
            List<CatalogEvent> events,
            LocalDateTime start,
            LocalDateTime end,
            String stationFilter,
            String typeFilter) {

        Map<String, EventStatistics> result = new TreeMap<>();

        for (CatalogEvent event : events) {

            if (event.time == null) {
                continue;
            }

            if (event.time.isBefore(start) || event.time.isAfter(end)) {
                continue;
            }

            if (stationFilter != null &&
                !stationFilter.equalsIgnoreCase("ALL") &&
                !stationFilter.trim().isEmpty()) {

                if (!stationFilter.equalsIgnoreCase(event.station)) {
                    continue;
                }
            }

            if (typeFilter != null &&
                !typeFilter.equalsIgnoreCase("ALL") &&
                !typeFilter.trim().isEmpty()) {

                if (!typeFilter.equalsIgnoreCase(event.eventType)) {
                    continue;
                }
            }

            EventStatistics stat = result.get(event.eventType);

            if (stat == null) {
                stat = new EventStatistics(event.eventType);
                result.put(event.eventType, stat);
            }

            stat.add(event);
        }

        return result;
    }

    public static Set<String> getStations(List<CatalogEvent> events) {

        Set<String> stations = new TreeSet<>();

        for (CatalogEvent e : events) {
            if (e.station != null && !e.station.trim().isEmpty()) {
                stations.add(e.station);
            }
        }

        return stations;
    }

    public static Set<String> getEventTypes(List<CatalogEvent> events) {

        Set<String> types = new TreeSet<>();

        for (CatalogEvent e : events) {
            if (e.eventType != null && !e.eventType.trim().isEmpty()) {
                types.add(e.eventType);
            }
        }

        return types;
    }

    public static LocalDateTime parseDateTime(String text) {

        return parseTime(text);
    }

    private static LocalDateTime parseTime(String text) {

        if (text == null) {
            throw new IllegalArgumentException("Empty time");
        }

        text = text.trim();

        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(text, fmt);
            } catch (Exception ignored) {
            }
        }

        throw new IllegalArgumentException("Unsupported time format: " + text);
    }

    private static double parseDouble(String text) {

        if (text == null || text.trim().isEmpty()) {
            return Double.NaN;
        }

        text = text.trim().replace(",", ".");

        try {
            return Double.parseDouble(text);
        } catch (Exception ex) {
            return Double.NaN;
        }
    }

    private static String detectDelimiter(String line) {

        if (line.contains(",")) {
            return ",";
        }

        if (line.contains("\t")) {
            return "\t";
        }

        return "\\s+";
    }

    private static String[] splitLine(String line, String delimiter) {

        if (delimiter.equals("\\s+")) {
            return line.trim().split("\\s+");
        }

        return line.split(delimiter);
    }

	private static Map<String, Integer> mapHeader(String[] headers) {

		Map<String, Integer> col = new HashMap<>();

		for (int i = 0; i < headers.length; i++) {

			String h = headers[i].trim().toLowerCase();

			if (h.equals("time") ||
				h.equals("datetime") ||
				h.equals("date_time") ||
				h.equals("origin_time") ||
				h.equals("origintime")) {
				col.put("time", i);
			}

			if (h.equals("station") ||
				h.equals("sta")) {
				col.put("station", i);
			}

			if (h.equals("type") ||
				h.equals("event_type") ||
				h.equals("class") ||
				h.equals("jenis") ||
				h.equals("classification")) {
				col.put("type", i);
			}

			// Priority:
			// 1. peakToPeakAmplitude
			// 2. maxAbsAmplitude
			// 3. generic amplitude

			if (h.equals("peaktopeakamplitude")) {

				col.put("amplitude", i);
			}
			else if (h.equals("maxabsamplitude")) {

				// only use if peak-to-peak not found yet
				if (!col.containsKey("amplitude")) {
					col.put("amplitude", i);
				}
			}
			else if (h.equals("amplitude") ||
					 h.equals("amp") ||
					 h.equals("amplitudo")) {

				// only use if no better amplitude column found
				if (!col.containsKey("amplitude")) {
					col.put("amplitude", i);
				}
			}

			if (h.equals("duration") ||
				h.equals("durasi") ||
				h.equals("dur") ||
				h.equals("durationsec")) {
				col.put("duration", i);
			}
		}

		return col;
	}

    private static String get(String[] row, Map<String, Integer> col, String name) {

        Integer idx = col.get(name);

        if (idx == null || idx < 0 || idx >= row.length) {
            return "";
        }

        return row[idx].trim();
    }
}