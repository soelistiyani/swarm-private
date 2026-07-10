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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class CatalogToolsMenu {

	private HelicorderViewPanel hvp;
	private File taggerRootDir;
	private double j2k;

	private static final String CATALOG_HEADER =
			"eventId,originTime,station,channel,classification,pTime,sTime,endTime,"
			+ "durationSec,Md,maxAbsAmplitude,maxAbsTime,peakToPeakAmplitude,"
			+ "dominantFrequencyHz,f1,f2,f3,"
			+ "operator,note";

	private static final String COUPLED_NOTE = "Coupled Events";
	private static final String COUPLED_CATALOG_HEADER =
			"eventId,originTime,station,channel,classification,note,"
			+ "p1Time,p2Time,p3Time,endTime,"
			+ "durationP2P1Sec,durationP3P2Sec,durationEndP1Sec,durationEndP2Sec,"
			+ "peakToPeakP2P1,peakToPeakP3P2,peakToPeakEndP2,amplitudeRatio,"
			+ "dominantFrequencyP2P1Hz,f1P2P1,f2P2P1,f3P2P1,"
			+ "dominantFrequencyP3P2Hz,f1P3P2,f2P3P2,f3P3P2,"
			+ "dominantFrequencyEndP2Hz,f1EndP2,f2EndP2,f3EndP2,"
			+ "operator";

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

	private static String defaultStartYmdUtc() {
		return "20260101";
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
						defaultStartYmdUtc());

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

			/*
			 * Satu backup besar untuk seluruh date range.
			 */
			try {
				backupCatalogDateRange(start, end);
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(
						hvp,
						"Failed to backup catalog date range:\n" + ex.getMessage(),
						"Backup Warning",
						JOptionPane.WARNING_MESSAGE);
			}

			java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.setTime(startDate);

			int count = 0;

			while (!cal.getTime().after(endDate)) {
				String ymd = sdf.format(cal.getTime());

				/*
				 * Rebuild harian tanpa backup per hari.
				 */
				rebuildCatalogForDay(ymd, true, false);

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
		rebuildCatalogForDay(ymd, true, true);
	}

	private void rebuildCatalogForDay(String ymd, boolean writeXlsx, boolean doBackup) {

		if (taggerRootDir == null) {
			JOptionPane.showMessageDialog(
					hvp,
					"Tagger root directory is not set.",
					"Catalog Tools",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		if (doBackup) {
			try {
				backupCatalogDay(ymd);
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(
						hvp,
						"Failed to backup catalog:\n" + ex.getMessage(),
						"Backup Warning",
						JOptionPane.WARNING_MESSAGE);
			}
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

		rebuildCatalogFromTags(tags, writeXlsx);

		ArrayList<File> coupledCsvFiles = new ArrayList<File>();
		collectCoupledTaggerCsvFiles(dayTaggerDir, coupledCsvFiles);

		ArrayList<TagData> coupledTags = new ArrayList<TagData>();

		for (File csvFile : coupledCsvFiles) {
			loadTagsFromFile(csvFile, coupledTags);
		}

		rebuildCoupledCatalogFromTags(coupledTags);
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

	private void collectCoupledTaggerCsvFiles(File dir, ArrayList<File> files) {

		File[] list = dir.listFiles();

		if (list == null) {
			return;
		}

		for (File f : list) {

			if (f.isDirectory()) {
				collectCoupledTaggerCsvFiles(f, files);

			} else if (f.isFile()
					&& f.getName().toLowerCase().endsWith(".csv")
					&& f.getName().startsWith("SwarmTaggerCoupled_")) {

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

	private void backupCatalogDateRange(
			String startYmd,
			String endYmd) throws IOException {

		if (taggerRootDir == null) {
			return;
		}

		String stamp =
				new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
						.format(new java.util.Date());

		File backupRoot = new File(
				new File(taggerRootDir, "backup"),
				"catalog_backup_" + stamp + "_range_" + startYmd + "_" + endYmd);

		java.text.SimpleDateFormat sdf =
				new java.text.SimpleDateFormat("yyyyMMdd");

		sdf.setLenient(false);

		try {
			java.util.Date startDate = sdf.parse(startYmd);
			java.util.Date endDate = sdf.parse(endYmd);

			java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.setTime(startDate);

			while (!cal.getTime().after(endDate)) {

				String ymd = sdf.format(cal.getTime());
				String year = ymd.substring(0, 4);

				File sourceDir = new File(
						new File(
								new File(taggerRootDir, "catalog"),
								year),
						ymd);

				if (sourceDir.exists()) {

					File targetDir = new File(
							new File(backupRoot, year),
							ymd);

					copyDirectory(sourceDir, targetDir);
				}

				cal.add(java.util.Calendar.DATE, 1);
			}

		} catch (java.text.ParseException ex) {
			throw new IOException("Invalid date range for backup.", ex);
		}
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
						defaultStartYmdUtc());

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
			boolean skipMdFrequency =
					EventClassificationRules
							.skipsDurationMagnitudeAndFrequency(classification);
			
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

				if (duration > 600
						&& !EventClassificationRules
								.allowsLongDuration(classification)) {
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

				if (!skipMdFrequency) {
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
					
					if (Double.isNaN(endTag.f1)
							|| Double.isNaN(endTag.f2)
							|| Double.isNaN(endTag.f3)) {

						warningCount++;
						sb.append("[WARNING] Missing frequency band f1/f2/f3 | ")
								.append(time).append(" | ")
								.append(station).append(" | ")
								.append(eventId).append("\n");
					}

					if (!Double.isNaN(endTag.f1)
							&& !Double.isNaN(endTag.f2)
							&& !Double.isNaN(endTag.f3)) {

						if (!(endTag.f1 > 0
								&& endTag.f2 > 0
								&& endTag.f3 > 0
								&& endTag.f1 <= endTag.f2
								&& endTag.f2 <= endTag.f3)) {

							warningCount++;
							sb.append("[WARNING] Invalid frequency band f1/f2/f3 | ")
									.append(time).append(" | ")
									.append(station).append(" | ")
									.append("f1=").append(endTag.f1).append(" Hz, ")
									.append("f2=").append(endTag.f2).append(" Hz, ")
									.append("f3=").append(endTag.f3).append(" Hz | ")
									.append(eventId).append("\n");
						}
					}
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

	private void rebuildCatalogFromTags(ArrayList<TagData> tags, boolean writeXlsx) {

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
			boolean skipMdFrequency =
					EventClassificationRules
							.skipsDurationMagnitudeAndFrequency(ref.classification);

			double dominantFrequencyHz =
					endTag == null || skipMdFrequency
							? Double.NaN : endTag.dominantFrequencyHz;

			double f1 =
					endTag == null || skipMdFrequency ? Double.NaN : endTag.f1;

			double f2 =
					endTag == null || skipMdFrequency ? Double.NaN : endTag.f2;

			double f3 =
					endTag == null || skipMdFrequency ? Double.NaN : endTag.f3;

			String md =
					endTag == null || skipMdFrequency ? ""
							: DurationMagnitudeUtil.format(
									DurationMagnitudeUtil.calculate(endTag.durationSec));

			if (endTag != null) {
			}

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
					+ csv(md) + ","
					+ csv(endTag == null ? "" : doubleString(endTag.maxAbsAmplitude)) + ","
					+ csv(endTag == null ? "" : endTag.maxAbsTime) + ","
					+ csv(endTag == null ? "" : doubleString(endTag.peakToPeakAmplitude)) + ","
					+ csv(doubleString(dominantFrequencyHz)) + ","
					+ csv(doubleString(f1)) + ","
					+ csv(doubleString(f2)) + ","
					+ csv(doubleString(f3)) + ","
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

				File allCsvFile =
						new File(
								dayCatalogDir,
								"Swarm_EventCatalog_" + ymd + "_ALL.csv");

				writeCatalogFile(
						allCsvFile,
						linesByDayAll.get(ymd));

				if (writeXlsx) {
					writeCatalogXlsxFile(
							new File(
									dayCatalogDir,
									"Swarm_EventCatalog_" + ymd + "_ALL.xlsx"),
							linesByDayAll.get(ymd));
				}

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

	private void rebuildCoupledCatalogFromTags(ArrayList<TagData> tags) {

		if (tags == null || tags.size() == 0) {
			return;
		}

		Map<String, ArrayList<String>> linesByDayAll =
				new LinkedHashMap<String, ArrayList<String>>();

		Map<String, Map<String, ArrayList<String>>> linesByDayChannel =
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
			TagData p1 = null;
			TagData p2 = null;
			TagData p3 = null;
			TagData end = null;

			for (TagData tag : eventTags) {
				if (!tag.isPhasePick()) {
					classTag = tag;
				}

				if ("CP1".equalsIgnoreCase(tag.phase)) {
					p1 = earliest(p1, tag);
				} else if ("CP2".equalsIgnoreCase(tag.phase)) {
					p2 = earliest(p2, tag);
				} else if ("CP3".equalsIgnoreCase(tag.phase)) {
					p3 = earliest(p3, tag);
				} else if ("CEND".equalsIgnoreCase(tag.phase)) {
					end = latest(end, tag);
				}
			}

			if (p1 == null || p2 == null || end == null) {
				continue;
			}

			TagData ref =
					classTag != null ? classTag :
					p1 != null ? p1 :
					p2 != null ? p2 :
					end;

			String ymd = formatJ2k(p1.startTime, "yyyyMMdd");
			String station = getStationCode(ref);
			String channelGroup = getStationChannelNetworkLocationCode(ref.channel);

			double durationP2P1 = p2.startTime - p1.startTime;
			double durationP3P2 =
					p3 == null ? Double.NaN : p3.startTime - p2.startTime;
			double durationEndP1 = end.startTime - p1.startTime;
			double durationEndP2 = end.startTime - p2.startTime;

			double peakToPeakP2P1 = p2.peakToPeakAmplitude;
			double peakToPeakP3P2 =
					p3 == null ? Double.NaN : p3.peakToPeakAmplitude;
			double peakToPeakEndP2 = end.peakToPeakAmplitude;

			double denominator = peakToPeakP2P1;
			double numerator =
					p3 == null ? peakToPeakEndP2 : peakToPeakP3P2;
			double amplitudeRatio =
					Double.isNaN(numerator)
							|| Double.isNaN(denominator)
							|| denominator == 0.0
							? Double.NaN : numerator / denominator;

			String line =
					csv(eventId) + ","
					+ csv(p1.getTimeString()) + ","
					+ csv(station) + ","
					+ csv(ref.channel) + ","
					+ csv(ref.classification) + ","
					+ csv(COUPLED_NOTE) + ","
					+ csv(p1.getTimeString()) + ","
					+ csv(p2.getTimeString()) + ","
					+ csv(p3 == null ? "" : p3.getTimeString()) + ","
					+ csv(end.getTimeString()) + ","
					+ csv(doubleString(durationP2P1)) + ","
					+ csv(doubleString(durationP3P2)) + ","
					+ csv(doubleString(durationEndP1)) + ","
					+ csv(doubleString(durationEndP2)) + ","
					+ csv(doubleString(peakToPeakP2P1)) + ","
					+ csv(doubleString(peakToPeakP3P2)) + ","
					+ csv(doubleString(peakToPeakEndP2)) + ","
					+ csv(doubleString(amplitudeRatio)) + ","
					+ csv(doubleString(p2.dominantFrequencyHz)) + ","
					+ csv(doubleString(p2.f1)) + ","
					+ csv(doubleString(p2.f2)) + ","
					+ csv(doubleString(p2.f3)) + ","
					+ csv(p3 == null ? "" : doubleString(p3.dominantFrequencyHz)) + ","
					+ csv(p3 == null ? "" : doubleString(p3.f1)) + ","
					+ csv(p3 == null ? "" : doubleString(p3.f2)) + ","
					+ csv(p3 == null ? "" : doubleString(p3.f3)) + ","
					+ csv(doubleString(end.dominantFrequencyHz)) + ","
					+ csv(doubleString(end.f1)) + ","
					+ csv(doubleString(end.f2)) + ","
					+ csv(doubleString(end.f3)) + ","
					+ csv(ref.operator);

			ArrayList<String> allLines = linesByDayAll.get(ymd);

			if (allLines == null) {
				allLines = new ArrayList<String>();
				linesByDayAll.put(ymd, allLines);
			}

			allLines.add(line);

			Map<String, ArrayList<String>> channelMap =
					linesByDayChannel.get(ymd);

			if (channelMap == null) {
				channelMap = new LinkedHashMap<String, ArrayList<String>>();
				linesByDayChannel.put(ymd, channelMap);
			}

			ArrayList<String> channelLines = channelMap.get(channelGroup);

			if (channelLines == null) {
				channelLines = new ArrayList<String>();
				channelMap.put(channelGroup, channelLines);
			}

			channelLines.add(line);
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
								"Swarm_CoupledEventCatalog_" + ymd + "_ALL.csv"),
						COUPLED_CATALOG_HEADER,
						linesByDayAll.get(ymd));

				File channelDir = new File(dayCatalogDir, "station");

				if (!channelDir.exists()) {
					channelDir.mkdirs();
				}

				Map<String, ArrayList<String>> channelMap =
						linesByDayChannel.get(ymd);

				for (String channelGroup : channelMap.keySet()) {
					writeCatalogFile(
							new File(
									channelDir,
									"Swarm_CoupledEventCatalog_"
											+ ymd + "_" + channelGroup + ".csv"),
							COUPLED_CATALOG_HEADER,
							channelMap.get(channelGroup));
				}
			}

		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
					hvp,
					ex.getMessage(),
					"Error rebuilding coupled catalog",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private static TagData earliest(TagData current, TagData candidate) {
		if (current == null || candidate.startTime < current.startTime) {
			return candidate;
		}

		return current;
	}

	private static TagData latest(TagData current, TagData candidate) {
		if (current == null || candidate.startTime > current.startTime) {
			return candidate;
		}

		return current;
	}

	public static void exportEventCatalogByDaySilent(
			HelicorderViewPanel hvp,
			File taggerRootDir) {

		exportEventCatalogByDay(hvp, taggerRootDir, true);
	}

	public static void rebuildCatalogForDaySilent(
			HelicorderViewPanel hvp,
			File taggerRootDir,
			String ymd) {

		if (hvp == null || taggerRootDir == null) {
			return;
		}

		if (ymd == null || ymd.length() == 0) {
			return;
		}

		CatalogToolsMenu tools =
				new CatalogToolsMenu(hvp, taggerRootDir);
				
		/*
		* Silent rebuild dipanggil otomatis setelah picking.
		* Jangan tulis XLSX di sini supaya klik END tetap ringan.
		*/
		tools.rebuildCatalogForDay(ymd, false, false);
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
			boolean skipMdFrequency =
					EventClassificationRules
							.skipsDurationMagnitudeAndFrequency(ref.classification);

			double dominantFrequencyHz =
					endTag == null || skipMdFrequency
							? Double.NaN : endTag.dominantFrequencyHz;

			double f1 =
					endTag == null || skipMdFrequency ? Double.NaN : endTag.f1;

			double f2 =
					endTag == null || skipMdFrequency ? Double.NaN : endTag.f2;

			double f3 =
					endTag == null || skipMdFrequency ? Double.NaN : endTag.f3;

			String md =
					endTag == null || skipMdFrequency ? ""
							: DurationMagnitudeUtil.format(
									DurationMagnitudeUtil.calculate(endTag.durationSec));

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
					+ csv(md) + ","
					+ csv(endTag == null ? "" : doubleString(endTag.maxAbsAmplitude)) + ","
					+ csv(endTag == null ? "" : endTag.maxAbsTime) + ","
					+ csv(endTag == null ? "" : doubleString(endTag.peakToPeakAmplitude)) + ","
					+ csv(doubleString(dominantFrequencyHz)) + ","
					+ csv(doubleString(f1)) + ","
					+ csv(doubleString(f2)) + ","
					+ csv(doubleString(f3)) + ","
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

				File allCsvFile =
						new File(
								dayCatalogDir,
								"Swarm_EventCatalog_" + ymd + "_ALL.csv");

				writeCatalogFile(
						allCsvFile,
						linesByDayAll.get(ymd));

				if (!silent) {
					writeCatalogXlsxFile(
							new File(
									dayCatalogDir,
									"Swarm_EventCatalog_" + ymd + "_ALL.xlsx"),
							linesByDayAll.get(ymd));
				}

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

		writeCatalogFile(outFile, CATALOG_HEADER, lines);
	}

	private static void writeCatalogFile(
			File outFile,
			String header,
			ArrayList<String> lines) throws IOException {

		BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));

		bw.write(header + "\n");

		for (String line : lines) {
			bw.write(line + "\n");
		}

		bw.close();
	}

	private static void writeCatalogXlsxFile(
			File outFile,
			ArrayList<String> lines) throws IOException {

		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Event Catalog");

		CreationHelper creationHelper = workbook.getCreationHelper();

		CellStyle headerStyle = workbook.createCellStyle();
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);
		headerStyle.setFont(headerFont);

		String[] headers = CATALOG_HEADER.split(",", -1);

		Row headerRow = sheet.createRow(0);

		for (int i = 0; i < headers.length; i++) {
			Cell cell = headerRow.createCell(i);
			cell.setCellValue(headers[i]);
			cell.setCellStyle(headerStyle);
		}

		int rowIndex = 1;

		for (String line : lines) {

			String[] cols = splitCsvLine(line);
			Row row = sheet.createRow(rowIndex++);

			for (int i = 0; i < headers.length; i++) {

				String value = i < cols.length ? cols[i] : "";
				Cell cell = row.createCell(i);

				/*
				 * Kolom waktu tetap disimpan sebagai teks dulu.
				 * Ini lebih aman karena format waktu event memakai UTC string
				 * dan tidak akan berubah karena setting regional Excel.
				 */
				if ("originTime".equals(headers[i])
						|| "pTime".equals(headers[i])
						|| "sTime".equals(headers[i])
						|| "endTime".equals(headers[i])
						|| "maxAbsTime".equals(headers[i])) {

					cell.setCellValue(value);

				} else {

					Double numberValue = parseDoubleOrNull(value);

					if (numberValue != null) {
						cell.setCellValue(numberValue.doubleValue());
					} else {
						cell.setCellValue(value);
					}
				}
			}
		}

		sheet.createFreezePane(0, 1);

		for (int i = 0; i < headers.length; i++) {
			if ("originTime".equals(headers[i])
					|| "pTime".equals(headers[i])
					|| "sTime".equals(headers[i])
					|| "endTime".equals(headers[i])
					|| "maxAbsTime".equals(headers[i])) {

				sheet.setColumnWidth(i, 24 * 256);

			} else {
				sheet.setColumnWidth(i, 18 * 256);
			}
		}

		FileOutputStream out = new FileOutputStream(outFile);
		workbook.write(out);
		out.close();

		workbook.close();
	}

	private static Double parseDoubleOrNull(String s) {

		if (s == null) {
			return null;
		}

		s = s.trim();

		if (s.length() == 0) {
			return null;
		}

		try {
			return Double.valueOf(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String[] splitCsvLine(String line) {

		ArrayList<String> result = new ArrayList<String>();

		if (line == null) {
			return new String[0];
		}

		StringBuilder sb = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {

			char c = line.charAt(i);

			if (c == '"') {

				if (inQuotes
						&& i + 1 < line.length()
						&& line.charAt(i + 1) == '"') {

					sb.append('"');
					i++;

				} else {
					inQuotes = !inQuotes;
				}

			} else if (c == ',' && !inQuotes) {

				result.add(sb.toString());
				sb.setLength(0);

			} else {
				sb.append(c);
			}
		}

		result.add(sb.toString());

		String[] arr = new String[result.size()];
		result.toArray(arr);

		return arr;
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

	private static String getStationChannelNetworkLocationCode(String channel) {
		if (channel == null || channel.trim().length() == 0) {
			return "UNKNOWN_UNKNOWN_UNKNOWN_UNKNOWN";
		}

		String normalized = sanitizeFileName(channel);
		String[] parts = normalized.split("_");

		ArrayList<String> meaningful = new ArrayList<String>();

		for (int i = 0; i < parts.length; i++) {
			String part = parts[i].trim();

			if (part.length() == 0) {
				continue;
			}

			if (meaningful.size() == 0 && "UNKNOWN".equalsIgnoreCase(part)) {
				continue;
			}

			meaningful.add(part);
		}

		while (meaningful.size() > 4
				&& "UNKNOWN".equalsIgnoreCase(
						meaningful.get(meaningful.size() - 1))) {
			meaningful.remove(meaningful.size() - 1);
		}

		if (meaningful.size() >= 4) {
			return meaningful.get(0)
					+ "_" + meaningful.get(1)
					+ "_" + meaningful.get(2)
					+ "_" + meaningful.get(3);
		}

		return normalized;
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
