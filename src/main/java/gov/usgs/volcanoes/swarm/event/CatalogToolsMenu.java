package gov.usgs.volcanoes.swarm.event;

import gov.usgs.volcanoes.swarm.heli.HelicorderViewPanel;
import gov.usgs.volcanoes.core.time.J2kSec;

import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;


public class CatalogToolsMenu {

	private HelicorderViewPanel hvp;
	private File taggerRootDir;
	private double j2k;

	public CatalogToolsMenu(HelicorderViewPanel hvp, File taggerRootDir) {
		this.hvp = hvp;
		this.taggerRootDir = taggerRootDir;
	}

	public void setJ2k(double j2k) {
		this.j2k = j2k;
	}

	public void setTaggerRootDir(File taggerRootDir) {
		this.taggerRootDir = taggerRootDir;
	}

/**
 * Menu untuk rebuild katalog.
 */
	public JMenu getRebuildCatalogMenu() {
	JMenu menu = new JMenu("Rebuild Catalog");

	JMenuItem currentDay = new JMenuItem("Rebuild Current Day");
	currentDay.addActionListener(e -> rebuildCatalogForCurrentDay());

	JMenuItem dateRange = new JMenuItem("Rebuild Date Range");
	dateRange.addActionListener(e -> rebuildCatalogForDateRange());

	menu.add(currentDay);
	menu.add(dateRange);

	return menu;
	}

	public JMenu getValidateCatalogMenu() {
		JMenu menu = new JMenu("Validate Catalog");

		JMenuItem currentDay = new JMenuItem("Validate Current Day");
		currentDay.addActionListener(e -> validateCurrentDay());

		JMenuItem dateRange = new JMenuItem("Validate Date Range");
		dateRange.addActionListener(e -> validateDateRange());

		menu.add(currentDay);
		menu.add(dateRange);

		return menu;
	}

	private static String formatJ2k(double j2k, String pattern) {
		SimpleDateFormat sdf = new SimpleDateFormat(pattern);
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date d = J2kSec.asDate(j2k);
		return sdf.format(d);
	}

	private static String todayYmdUtc() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdf.format(new Date());
	}

	private void rebuildCatalogForCurrentDay() {

		String ymd =
				JOptionPane.showInputDialog(
						hvp,
						"Date to rebuild (YYYYMMDD, in UTC):",
						todayYmdUtc());

		if (ymd == null || ymd.trim().length() == 0) {
			return;
		}

		ymd = ymd.trim();

		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
			sdf.setLenient(false);
			sdf.parse(ymd);

			rebuildCatalogForDay(ymd);

			JOptionPane.showMessageDialog(
					hvp,
					"Catalog rebuilt for day:\n" + ymd);

		} catch (Exception ex) {
			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Invalid Date",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void rebuildCatalogForDateRange() {

		String start =
				JOptionPane.showInputDialog(
						hvp,
						"Start date (YYYYMMDD, in UTC):",
						formatJ2k(j2k, "yyyyMMdd"));

		if (start == null || start.trim().length() == 0) {
			return;
		}

		String end =
				JOptionPane.showInputDialog(
						hvp,
						"End date (YYYYMMDD, in UTC):",
						start);

		if (end == null || end.trim().length() == 0) {
			return;
		}

		start = start.trim();
		end = end.trim();

		try {
			java.text.SimpleDateFormat sdf =
					new java.text.SimpleDateFormat("yyyyMMdd");

			sdf.setLenient(false);

			java.util.Date startDate = sdf.parse(start);
			java.util.Date endDate = sdf.parse(end);

			if (endDate.before(startDate)) {
				JOptionPane.showMessageDialog(
						hvp,
						"End date must be after start date.",
						"Invalid Date Range",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.setTime(startDate);

			int count = 0;

			while (!cal.getTime().after(endDate)) {
				String ymd = sdf.format(cal.getTime());
				rebuildCatalogForDay(ymd);
				count++;

				cal.add(java.util.Calendar.DATE, 1);
			}

			JOptionPane.showMessageDialog(
					hvp,
					"Catalog rebuilt for "
					+ count
					+ " day(s).\n"
					+ start
					+ " to "
					+ end);

		} catch (Exception ex) {
			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Invalid Date",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void rebuildCatalogForDay(String ymd) {

		if (taggerRootDir == null) {
			JOptionPane.showMessageDialog(
					hvp,
					"Tagger root directory is not set.",
					"Catalog Tools",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		try {
			backupCatalogDay(ymd);
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
					hvp,
					"Failed to backup catalog:\n" + ex.getMessage(),
					"Backup Warning",
					JOptionPane.WARNING_MESSAGE);
		}

		String year = ymd.substring(0, 4);

		File dayTaggerDir = new File(
				new File(
						new File(taggerRootDir, "tagger"),
						year),
				ymd);

		if (!dayTaggerDir.exists() || !dayTaggerDir.isDirectory()) {
			return;
		}

		ArrayList<File> csvFiles = new ArrayList<File>();
		collectTaggerCsvFiles(dayTaggerDir, csvFiles);

		ArrayList<TagData> tags = new ArrayList<TagData>();

		for (File csvFile : csvFiles) {
			loadTagsFromFile(csvFile, tags);
		}

		rebuildCatalogFromTags(tags);
	}

	private void collectTaggerCsvFiles(File dir, ArrayList<File> files) {

		File[] list = dir.listFiles();

		if (list == null) {
			return;
		}

		for (File f : list) {

			if (f.isDirectory()) {
				collectTaggerCsvFiles(f, files);

			} else if (f.isFile()
					&& f.getName().toLowerCase().endsWith(".csv")
					&& f.getName().startsWith("SwarmTagger_")) {

				files.add(f);
			}
		}
	}

	private void backupCatalogDay(String ymd) throws IOException {

		String year = ymd.substring(0, 4);

		File sourceDir = new File(
				new File(
						new File(taggerRootDir, "catalog"),
						year),
				ymd);

		if (!sourceDir.exists()) {
			return;
		}

		String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
				.format(new java.util.Date());

		File backupDir = new File(
				new File(
						new File(taggerRootDir, "backup"),
						"catalog_backup_" + stamp),
				year + File.separator + ymd);

		copyDirectory(sourceDir, backupDir);
	}

	private void copyDirectory(File source, File target) throws IOException {

		if (source.isDirectory()) {
			if (!target.exists()) {
				target.mkdirs();
			}

			File[] files = source.listFiles();

			if (files != null) {
				for (File f : files) {
					copyDirectory(f, new File(target, f.getName()));
				}
			}

		} else {
			BufferedInputStream in =
					new BufferedInputStream(new FileInputStream(source));

			BufferedOutputStream out =
					new BufferedOutputStream(new FileOutputStream(target));

			byte[] buffer = new byte[8192];
			int len;

			while ((len = in.read(buffer)) > 0) {
				out.write(buffer, 0, len);
			}

			in.close();
			out.close();
		}
	}

	private void validateCurrentDay() {

		String ymd =
				JOptionPane.showInputDialog(
						hvp,
						"Date to validate (YYYYMMDD, in UTC):",
						todayYmdUtc());

		if (ymd == null || ymd.trim().length() == 0) {
			return;
		}

		ymd = ymd.trim();

		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
			sdf.setLenient(false);
			sdf.parse(ymd);

			ArrayList<TagData> tags = loadTagsForDay(ymd);

			String report =
					"Validation Date: " + ymd + "\n\n"
					+ validateTags(tags);

			showValidationReport(report, "Validate Current Day");

		} catch (Exception ex) {
			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Invalid Date",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void validateDateRange() {

		String start =
				JOptionPane.showInputDialog(
						hvp,
						"Start date (YYYYMMDD, in UTC):",
						formatJ2k(j2k, "yyyyMMdd"));

		if (start == null || start.trim().length() == 0) {
			return;
		}

		String end =
				JOptionPane.showInputDialog(
						hvp,
						"End date (YYYYMMDD, in UTC):",
						start);

		if (end == null || end.trim().length() == 0) {
			return;
		}

		start = start.trim();
		end = end.trim();

		try {
			java.text.SimpleDateFormat sdf =
					new java.text.SimpleDateFormat("yyyyMMdd");

			sdf.setLenient(false);

			java.util.Date startDate = sdf.parse(start);
			java.util.Date endDate = sdf.parse(end);

			if (endDate.before(startDate)) {
				JOptionPane.showMessageDialog(
						hvp,
						"End date must be after start date.",
						"Invalid Date Range",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			ArrayList<TagData> allTags = new ArrayList<TagData>();

			java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.setTime(startDate);

			int count = 0;

			while (!cal.getTime().after(endDate)) {

				String ymd = sdf.format(cal.getTime());

				ArrayList<TagData> dayTags = loadTagsForDay(ymd);
				allTags.addAll(dayTags);

				count++;

				cal.add(java.util.Calendar.DATE, 1);
			}

			String report =
					"Validation Date Range: "
					+ start
					+ " to "
					+ end
					+ "\nDays checked: "
					+ count
					+ "\n\n"
					+ validateTags(allTags);

			showValidationReport(report, "Validate Date Range");

		} catch (Exception ex) {
			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Invalid Date",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private String validateTags(ArrayList<TagData> tags) {

		StringBuilder sb = new StringBuilder();

		Map<String, ArrayList<TagData>> eventsById =
				new LinkedHashMap<String, ArrayList<TagData>>();

		for (TagData tag : tags) {

			if (tag.eventId == null || tag.eventId.length() == 0) {
				sb.append("Missing eventId: ")
						.append(tag.toString())
						.append("\n");
				continue;
			}

			ArrayList<TagData> eventTags = eventsById.get(tag.eventId);

			if (eventTags == null) {
				eventTags = new ArrayList<TagData>();
				eventsById.put(tag.eventId, eventTags);
			}

			eventTags.add(tag);
		}

		int warningCount = 0;

		for (String eventId : eventsById.keySet()) {

			ArrayList<TagData> eventTags = eventsById.get(eventId);

			TagData classTag = null;
			TagData pTag = null;
			TagData sTag = null;
			TagData endTag = null;

			int pCount = 0;
			int sCount = 0;
			int endCount = 0;

			for (TagData tag : eventTags) {

				if (!tag.isPhasePick()) {
					classTag = tag;
				}

				if (tag.isP()) {
					pCount++;
					if (pTag == null || tag.startTime < pTag.startTime) {
						pTag = tag;
					}
				}

				if (tag.isS()) {
					sCount++;
					if (sTag == null || tag.startTime < sTag.startTime) {
						sTag = tag;
					}
				}

				if (tag.isCoda()) {
					endCount++;
					if (endTag == null || tag.startTime > endTag.startTime) {
						endTag = tag;
					}
				}
			}

			TagData ref =
					pTag != null ? pTag :
					classTag != null ? classTag :
					sTag != null ? sTag :
					endTag;

			String time = ref == null ? "" : ref.getTimeString();
			String station = ref == null ? "" : getStationCode(ref);
			String classification = ref == null ? "" : ref.classification;
			
			if (classification == null || classification.trim().length() == 0) {
			    warningCount++;
			    sb.append("[WARNING] Empty classification | ")
			            .append(time).append(" | ")
			            .append(station).append(" | ")
			            .append(eventId).append("\n");
			}

			if (classTag == null) {
				warningCount++;
				sb.append("[WARNING] Missing classification | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (pTag == null) {
				warningCount++;
				sb.append("[WARNING] Missing P | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(classification).append(" | ")
						.append(eventId).append("\n");
			}

			if (endTag == null) {
				warningCount++;
				sb.append("[WARNING] Missing END | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(classification).append(" | ")
						.append(eventId).append("\n");
			}

			if (pCount > 1) {
				warningCount++;
				sb.append("[WARNING] Multiple P picks | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (sCount > 1) {
				warningCount++;
				sb.append("[WARNING] Multiple S picks | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (endCount > 1) {
				warningCount++;
				sb.append("[WARNING] Multiple END picks | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (pTag != null && sTag != null && sTag.startTime < pTag.startTime) {
				warningCount++;
				sb.append("[WARNING] S before P | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (pTag != null && endTag != null && endTag.startTime <= pTag.startTime) {
				warningCount++;
				sb.append("[WARNING] END before/equal P | ")
						.append(time).append(" | ")
						.append(station).append(" | ")
						.append(eventId).append("\n");
			}

			if (pTag != null && endTag != null && endTag.startTime > pTag.startTime) {
				double duration = endTag.startTime - pTag.startTime;

				if (duration < 0.5) {
					warningCount++;
					sb.append("[WARNING] Duration too short (<0.5 s) | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(duration).append(" s | ")
							.append(eventId).append("\n");
				}

				if (duration > 600) {
					warningCount++;
					sb.append("[WARNING] Duration too long (>600 s) | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(duration).append(" s | ")
							.append(eventId).append("\n");
				}
				
				if (Double.isNaN(endTag.maxAbsAmplitude)) {
					warningCount++;
					sb.append("[WARNING] Missing max amplitude | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(eventId).append("\n");
				}

				if (Double.isNaN(endTag.peakToPeakAmplitude)) {
					warningCount++;
					sb.append("[WARNING] Missing peak-to-peak amplitude | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(eventId).append("\n");
				}

				if (Double.isNaN(endTag.dominantFrequencyHz)) {
					warningCount++;
					sb.append("[WARNING] Missing dominant frequency | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(eventId).append("\n");
				}

				if (!Double.isNaN(endTag.dominantFrequencyHz)
						&& endTag.dominantFrequencyHz <= 0) {
					warningCount++;
					sb.append("[WARNING] Invalid dominant frequency | ")
							.append(time).append(" | ")
							.append(station).append(" | ")
							.append(endTag.dominantFrequencyHz).append(" Hz | ")
							.append(eventId).append("\n");
				}
			}
		}

		if (warningCount == 0) {
			return "Validation complete.\nNo issues found.\n\nTotal events: "
					+ eventsById.size()
					+ "\nTotal tags: "
					+ tags.size();
		}

		return "Validation complete.\n"
				+ "Total events: " + eventsById.size() + "\n"
				+ "Total tags: " + tags.size() + "\n"
				+ "Warnings: " + warningCount + "\n\n"
				+ sb.toString();
	}

	private void showValidationReport(String report, String title) {

		JTextArea textArea = new JTextArea(report);
		textArea.setEditable(false);
		textArea.setLineWrap(false);

		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setPreferredSize(new Dimension(850, 500));

		JOptionPane.showMessageDialog(
				hvp,
				scrollPane,
				title,
				JOptionPane.INFORMATION_MESSAGE);
	}

	private void loadTagsFromFile(File file, ArrayList<TagData> tags) {

		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;

			while ((line = br.readLine()) != null) {

				line = line.trim();

				if (line.length() == 0) {
					continue;
				}

				if (line.toLowerCase().startsWith("time,")) {
					continue;
				}

				try {
					TagData tag = new TagData(line);

					if (!containsTag(tags, tag)) {
						tags.add(tag);
					}

				} catch (Exception ex) {
					System.err.println(
							"Skipping bad tagger line in "
							+ file.getAbsolutePath()
							+ ": "
							+ line);
				}
			}

			br.close();

		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Error reading tagger file",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private ArrayList<TagData> loadTagsForDay(String ymd) {

		ArrayList<TagData> tags = new ArrayList<TagData>();

		if (taggerRootDir == null) {
			JOptionPane.showMessageDialog(
					hvp,
					"Tagger root directory is not set.",
					"Catalog Tools",
					JOptionPane.WARNING_MESSAGE);
			return tags;
		}

		String year = ymd.substring(0, 4);

		File dayTaggerDir =
				new File(
						new File(
								new File(taggerRootDir, "tagger"),
								year),
						ymd);

		if (!dayTaggerDir.exists() || !dayTaggerDir.isDirectory()) {
			return tags;
		}

		ArrayList<File> csvFiles = new ArrayList<File>();
		collectTaggerCsvFiles(dayTaggerDir, csvFiles);

		for (File csvFile : csvFiles) {
			loadTagsFromFile(csvFile, tags);
		}

		return tags;
	}

	private boolean containsTag(ArrayList<TagData> tags, TagData newTag) {

		for (TagData tag : tags) {
			if (tag.channel.equals(newTag.channel)
					&& tag.eventId.equals(newTag.eventId)
					&& tag.phase.equals(newTag.phase)
					&& Math.abs(tag.startTime - newTag.startTime) < 0.001) {
				return true;
			}
		}

		return false;
	}

	private void rebuildCatalogFromTags(ArrayList<TagData> tags) {

		Map<String, ArrayList<String>> linesByDayAll =
				new LinkedHashMap<String, ArrayList<String>>();

		Map<String, Map<String, ArrayList<String>>> linesByDayStation =
				new LinkedHashMap<String, Map<String, ArrayList<String>>>();

		Map<String, ArrayList<TagData>> eventsById =
				new LinkedHashMap<String, ArrayList<TagData>>();

		for (TagData tag : tags) {

			if (tag.eventId == null || tag.eventId.length() == 0) {
				continue;
			}

			ArrayList<TagData> eventTags = eventsById.get(tag.eventId);

			if (eventTags == null) {
				eventTags = new ArrayList<TagData>();
				eventsById.put(tag.eventId, eventTags);
			}

			eventTags.add(tag);
		}

		for (String eventId : eventsById.keySet()) {

			ArrayList<TagData> eventTags = eventsById.get(eventId);

			TagData classTag = null;
			TagData pTag = null;
			TagData sTag = null;
			TagData endTag = null;

			for (TagData tag : eventTags) {

				if (!tag.isPhasePick()) {
					classTag = tag;
				}

				if (tag.isP()) {
					if (pTag == null || tag.startTime < pTag.startTime) {
						pTag = tag;
					}
				}

				if (tag.isS()) {
					if (sTag == null || tag.startTime < sTag.startTime) {
						sTag = tag;
					}
				}

				if (tag.isCoda()) {
					if (endTag == null || tag.startTime > endTag.startTime) {
						endTag = tag;
					}
				}
			}

			TagData ref =
					pTag != null ? pTag :
					classTag != null ? classTag :
					sTag != null ? sTag :
					endTag;

			if (ref == null) {
				continue;
			}

			String ymd = formatJ2k(ref.startTime, "yyyyMMdd");
			String station = getStationCode(ref);

			double dominantFrequencyHz =
					endTag == null ? Double.NaN : endTag.dominantFrequencyHz;

			String line =
					csv(eventId) + ","
					+ csv(pTag == null ? ref.getTimeString() : pTag.getTimeString()) + ","
					+ csv(station) + ","
					+ csv(ref.channel) + ","
					+ csv(ref.classification) + ","
					+ csv(pTag == null ? "" : pTag.getTimeString()) + ","
					+ csv(sTag == null ? "" : sTag.getTimeString()) + ","
					+ csv(endTag == null ? "" : endTag.getTimeString()) + ","
					+ csv(endTag == null ? "" : doubleString(endTag.durationSec)) + ","
					+ csv(endTag == null ? "" : doubleString(endTag.maxAbsAmplitude)) + ","
					+ csv(endTag == null ? "" : endTag.maxAbsTime) + ","
					+ csv(endTag == null ? "" : doubleString(endTag.peakToPeakAmplitude)) + ","
					+ csv(doubleString(dominantFrequencyHz)) + ","
					+ csv(ref.operator) + ","
					+ csv(ref.note);

			ArrayList<String> allLines = linesByDayAll.get(ymd);

			if (allLines == null) {
				allLines = new ArrayList<String>();
				linesByDayAll.put(ymd, allLines);
			}

			allLines.add(line);

			Map<String, ArrayList<String>> stationMap =
					linesByDayStation.get(ymd);

			if (stationMap == null) {
				stationMap = new LinkedHashMap<String, ArrayList<String>>();
				linesByDayStation.put(ymd, stationMap);
			}

			ArrayList<String> stationLines = stationMap.get(station);

			if (stationLines == null) {
				stationLines = new ArrayList<String>();
				stationMap.put(station, stationLines);
			}

			stationLines.add(line);
		}

		try {
			for (String ymd : linesByDayAll.keySet()) {

				String year = ymd.substring(0, 4);

				File dayCatalogDir = new File(
						new File(
								new File(taggerRootDir, "catalog"),
								year),
						ymd);

				if (!dayCatalogDir.exists()) {
					dayCatalogDir.mkdirs();
				}

				writeCatalogFile(
						new File(
								dayCatalogDir,
								"Swarm_EventCatalog_" + ymd + "_ALL.csv"),
						linesByDayAll.get(ymd));

				File stationDir = new File(dayCatalogDir, "station");

				if (!stationDir.exists()) {
					stationDir.mkdirs();
				}

				Map<String, ArrayList<String>> stationMap =
						linesByDayStation.get(ymd);

				for (String station : stationMap.keySet()) {
					writeCatalogFile(
							new File(
									stationDir,
									"Swarm_EventCatalog_" + ymd + "_" + station + ".csv"),
							stationMap.get(station));
				}
			}

		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Error rebuilding catalog",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	public static void exportEventCatalogByDaySilent(
			HelicorderViewPanel hvp,
			File taggerRootDir) {

		exportEventCatalogByDay(hvp, taggerRootDir, true);
	}

	private static void exportEventCatalogByDay(
			HelicorderViewPanel hvp,
			File taggerRootDir,
			boolean silent) {

		if (hvp == null || taggerRootDir == null) {
			return;
		}

		Map<String, ArrayList<String>> linesByDayAll =
				new LinkedHashMap<String, ArrayList<String>>();

		Map<String, Map<String, ArrayList<String>>> linesByDayStation =
				new LinkedHashMap<String, Map<String, ArrayList<String>>>();

		/*
		 * GROUP TAGS BY EVENT ID FIRST.
		 * This avoids scanning all tagData repeatedly for every event.
		 */
		Map<String, ArrayList<TagData>> eventsById =
				new LinkedHashMap<String, ArrayList<TagData>>();

		for (TagData tag : hvp.getTagData()) {

			if (tag.eventId == null || tag.eventId.length() == 0) {
				continue;
			}

			ArrayList<TagData> eventTags = eventsById.get(tag.eventId);

			if (eventTags == null) {
				eventTags = new ArrayList<TagData>();
				eventsById.put(tag.eventId, eventTags);
			}

			eventTags.add(tag);
		}

		for (String eventId : eventsById.keySet()) {

			ArrayList<TagData> eventTags = eventsById.get(eventId);

			TagData classTag = null;
			TagData pTag = null;
			TagData sTag = null;
			TagData endTag = null;

			for (TagData tag : eventTags) {

				if (!tag.isPhasePick()) {
					classTag = tag;
				}

				if (tag.isP()) {
					if (pTag == null || tag.startTime < pTag.startTime) {
						pTag = tag;
					}
				}

				if (tag.isS()) {
					if (sTag == null || tag.startTime < sTag.startTime) {
						sTag = tag;
					}
				}

				if ("END".equalsIgnoreCase(tag.phase)
						|| "CODA".equalsIgnoreCase(tag.phase)) {
					if (endTag == null || tag.startTime > endTag.startTime) {
						endTag = tag;
					}
				}
			}

			TagData ref =
					pTag != null ? pTag :
					classTag != null ? classTag :
					sTag != null ? sTag :
					endTag;

			if (ref == null) {
				continue;
			}

			String ymd = formatJ2k(ref.startTime, "yyyyMMdd");
			String station = getStationCode(ref);

			double dominantFrequencyHz =
					endTag == null ? Double.NaN : endTag.dominantFrequencyHz;

			String line =
					csv(eventId) + ","
					+ csv(pTag == null ? ref.getTimeString() : pTag.getTimeString()) + ","
					+ csv(station) + ","
					+ csv(ref.channel) + ","
					+ csv(ref.classification) + ","
					+ csv(pTag == null ? "" : pTag.getTimeString()) + ","
					+ csv(sTag == null ? "" : sTag.getTimeString()) + ","
					+ csv(endTag == null ? "" : endTag.getTimeString()) + ","
					+ csv(endTag == null ? "" : doubleString(endTag.durationSec)) + ","
					+ csv(endTag == null ? "" : doubleString(endTag.maxAbsAmplitude)) + ","
					+ csv(endTag == null ? "" : endTag.maxAbsTime) + ","
					+ csv(endTag == null ? "" : doubleString(endTag.peakToPeakAmplitude)) + ","
					+ csv(doubleString(dominantFrequencyHz)) + ","
					+ csv(ref.operator) + ","
					+ csv(ref.note);

			ArrayList<String> allLines = linesByDayAll.get(ymd);

			if (allLines == null) {
				allLines = new ArrayList<String>();
				linesByDayAll.put(ymd, allLines);
			}

			allLines.add(line);

			Map<String, ArrayList<String>> stationMap =
					linesByDayStation.get(ymd);

			if (stationMap == null) {
				stationMap = new LinkedHashMap<String, ArrayList<String>>();
				linesByDayStation.put(ymd, stationMap);
			}

			ArrayList<String> stationLines = stationMap.get(station);

			if (stationLines == null) {
				stationLines = new ArrayList<String>();
				stationMap.put(station, stationLines);
			}

			stationLines.add(line);
		}

		try {

			for (String ymd : linesByDayAll.keySet()) {

				String year = ymd.substring(0, 4);

				File dayCatalogDir =
						new File(
								new File(
										new File(taggerRootDir, "catalog"),
										year),
								ymd);

				if (!dayCatalogDir.exists()) {
					dayCatalogDir.mkdirs();
				}

				writeCatalogFile(
						new File(
								dayCatalogDir,
								"Swarm_EventCatalog_" + ymd + "_ALL.csv"),
						linesByDayAll.get(ymd));

				File stationDir = new File(dayCatalogDir, "station");

				if (!stationDir.exists()) {
					stationDir.mkdirs();
				}

				Map<String, ArrayList<String>> stationMap =
						linesByDayStation.get(ymd);

				for (String station : stationMap.keySet()) {
					writeCatalogFile(
							new File(
									stationDir,
									"Swarm_EventCatalog_" + ymd + "_" + station + ".csv"),
							stationMap.get(station));
				}
			}

			if (!silent) {
				JOptionPane.showMessageDialog(
						hvp,
						"Event catalog exported by day to:\n"
						+ new File(taggerRootDir, "catalog").getAbsolutePath());
			}

		} catch (IOException ex) {

			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Error exporting catalog",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private static void writeCatalogFile(
			File outFile,
			ArrayList<String> lines) throws IOException {

		BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));

		bw.write("eventId,originTime,station,channel,classification,pTime,sTime,endTime,"
				+ "durationSec,maxAbsAmplitude,maxAbsTime,peakToPeakAmplitude,"
				+ "dominantFrequencyHz,"
				+ "operator,note\n");

		for (String line : lines) {
			bw.write(line + "\n");
		}

		bw.close();
	}

	private static String getStationCode(TagData tag) {
		String ch = tag == null ? null : tag.channel;

		if (ch == null || ch.trim().length() == 0) {
			return "UNKNOWN";
		}

		String[] parts = ch.split("\\.");

		if (parts.length >= 2) {
			return sanitizeFileName(parts[1]);
		}

		return sanitizeFileName(ch);
	}

	private static String sanitizeFileName(String s) {
		if (s == null) {
			return "";
		}

		return s.trim().replaceAll("[^A-Za-z0-9_-]", "_");
	}

	private static String csv(String s) {

		if (s == null) {
			return "";
		}

		s = s.replace("\"", "\"\"");

		if (s.indexOf(",") >= 0
				|| s.indexOf("\"") >= 0
				|| s.indexOf("\n") >= 0
				|| s.indexOf("\r") >= 0) {

			return "\"" + s + "\"";
		}

		return s;
	}

	private static String doubleString(double d) {

		if (Double.isNaN(d)) {
			return "";
		}

		return Double.toString(d);
	}
}