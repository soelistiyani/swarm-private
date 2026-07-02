package gov.usgs.volcanoes.swarm.event.gad;

import gov.usgs.volcanoes.quakeml.Pick;
import gov.usgs.volcanoes.swarm.event.PickData;
import gov.usgs.volcanoes.swarm.wave.WaveClipboardFrame;
import gov.usgs.volcanoes.swarm.wave.WaveViewPanel;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

public class GadManager {

  private File gadDir = new File(System.getProperty("user.dir"), "GAD");
  private File gadExe = new File(gadDir, "GAD.exe");
  private File arrivalFile = new File(gadDir, "Arrival.dat");
  private File velocityFile = new File(gadDir, "velocity.dat");
  private File stationFile = new File(gadDir, "station.dat");
  private File summitFile = new File(gadDir, "summit.dat");
  private File resultFile = new File(gadDir, "Results.dat");
  private File arrivalHistoryFile = new File(gadDir, "Arrival_history.dat");
  private File resultHistoryFile = new File(gadDir, "Result_history.dat");
  private File hypocenterCsvFile = new File(gadDir, "hypocenter.csv");

  private String resultText = "";
  private GadResult result;
  
  public void setFiles(
    String folder,
    String velocity,
    String station,
    String summit,
    String result) {

  gadDir = new File(folder);
  gadExe = new File(gadDir, "GAD.exe");
  arrivalFile = new File(gadDir, "Arrival.dat");
  arrivalHistoryFile = new File(gadDir, "Arrival_history.dat");
  velocityFile = new File(velocity);
  stationFile = new File(station);
  summitFile = new File(summit);
  resultFile = new File(result);
  resultHistoryFile = new File(gadDir, "Result_history.dat");
  hypocenterCsvFile = new File(gadDir, "hypocenter.csv");
  }
 
	public void setCatalogRoot(String folder) {
	  catalogRoot = new File(folder);
	}
	
	private CatalogEvent selectCatalogEventNearPick(long referencePTime) throws IOException {

		java.util.List<CatalogEvent> candidates =
		  findCatalogEventsNear(referencePTime, 60000L);

		if (candidates.size() == 0) {
		  int opt = JOptionPane.showConfirmDialog(
			  null,
			  "Tidak ada event catalog yang cocok dalam ±60 detik dari P pick.\n"
				  + "GAD tetap dapat dijalankan dengan eventId sementara dari P pertama.\n\n"
				  + "Lanjutkan Run GAD tanpa link ke Event Catalog?",
			  "Event Catalog Tidak Ditemukan",
			  JOptionPane.YES_NO_OPTION,
			  JOptionPane.WARNING_MESSAGE
		  );

		  if (opt != JOptionPane.YES_OPTION) {
			return null;
		  }

		  return createEventFromFirstPick(referencePTime);
		}

		Object[] options = new Object[candidates.size()];

		for (int i = 0; i < candidates.size(); i++) {
		CatalogEvent ev = candidates.get(i);

		double dt =
			Math.abs(ev.eventTime - referencePTime) / 1000.0;

		options[i] =
			ev.eventId
			+ " | "
			+ formatUtc(ev.eventTime)
			+ " | "
			+ ev.classification
			+ " | "
			+ ev.station
			+ " | Δ "
			+ String.format(Locale.US, "%.1f s", dt);
		}

		Object selected = JOptionPane.showInputDialog(
		  null,
		  "Pilih event catalog untuk pick Wave Clipboard:",
		  "Link Event Catalog",
		  JOptionPane.QUESTION_MESSAGE,
		  null,
		  options,
		  options[0]
		);

		if (selected == null) {
		return null;
		}

		for (int i = 0; i < options.length; i++) {
		if (options[i].equals(selected)) {
		  return candidates.get(i);
		}
		}

		return null;
	}

	private File catalogRoot = loadCatalogRootFromTaggerConfig();
	
	private File loadCatalogRootFromTaggerConfig() {

	  File configFile =
		  new File(
			  System.getProperty("user.dir"),
			  "TaggerDirectory.config"
		  );

	  if (!configFile.exists()) {
		return new File(
			System.getProperty("user.home"),
			"SwarmTagger" + File.separator + "Catalog"
		);
	  }

	  BufferedReader br = null;

	  try {
		br = new BufferedReader(new FileReader(configFile));

		String line;

		while ((line = br.readLine()) != null) {

		  line = line.trim();

		  if (line.length() == 0 || line.startsWith("#")) {
			continue;
		  }

		  File taggerRoot = new File(line);

		  File catalogDir = new File(taggerRoot, "Catalog");

		  if (catalogDir.exists()) {
			return catalogDir;
		  }

		  return taggerRoot;
		}

	  } catch (IOException e) {
		System.out.println(
			"GAD failed to read TaggerDirectory.config: "
			+ e.getMessage()
		);

	  } finally {
		if (br != null) {
		  try {
			br.close();
		  } catch (IOException ignored) {
		  }
		}
	  }

	  return new File(
		  System.getProperty("user.home"),
		  "SwarmTagger" + File.separator + "Catalog"
	  );
	}
	
	private java.util.List<CatalogEvent> findCatalogEventsNear(long referenceTime, long toleranceMillis) throws IOException {

	  java.util.List<CatalogEvent> events =
		  new java.util.ArrayList<CatalogEvent>();
		  
	  File catalogFile = getEventCatalogFile(referenceTime);

	  if (!catalogFile.exists()) {
		return events;
	  }

	  BufferedReader br =
		  new BufferedReader(new FileReader(catalogFile));

	  String header = br.readLine();

	  if (header == null) {
		br.close();
		return events;
	  }

	  String[] cols = header.split(",", -1);

	  int eventIdCol = findColumn(cols, "eventId");
	  int pTimeCol = findAnyColumn(
	  	cols,
	  	new String[] {"pTime", "p_time", "ptime", "pickTime"}
	  );

	  int originTimeCol = findAnyColumn(
	  	cols,
	  	new String[] {"originTime", "time", "eventTime", "startTime"}
	  );
	  int stationCol = findAnyColumn(
	      cols,
	      new String[] {"station", "sta"}
	  );
	  int classCol = findAnyColumn(
	  	cols,
	  	new String[] {"classification", "type", "eventType", "jenis"}
	  );

	  String line;

	  while ((line = br.readLine()) != null) {

		String[] v = line.split(",", -1);

		CatalogEvent ev = new CatalogEvent();

		ev.eventId = getCsvValue(v, eventIdCol);
		ev.station = getCsvValue(v, stationCol);
		ev.classification = getCsvValue(v, classCol);

		String timeText = getCsvValue(v, pTimeCol);

		if (timeText.length() == 0) {
		  timeText = getCsvValue(v, originTimeCol);
		}

		if (ev.eventId.length() == 0 || timeText.length() == 0) {
		  continue;
		}

		try {
		  ev.eventTime = parseCsvTime(timeText);
		  ev.baseTime = floorToMinuteUtc(ev.eventTime);
		  ev.gadEventId = makeGadEventId(ev.eventTime);
		} catch (Exception e) {
		  continue;
		}

		long dt = Math.abs(ev.eventTime - referenceTime);

		if (dt <= toleranceMillis) {
		  events.add(ev);
		}
	  }

	  br.close();

	  final long ref = referenceTime;

	  java.util.Collections.sort(
		  events,
		  new java.util.Comparator<CatalogEvent>() {
			public int compare(CatalogEvent a, CatalogEvent b) {
			  long da = Math.abs(a.eventTime - ref);
			  long db = Math.abs(b.eventTime - ref);

			  if (da < db) {
				return -1;
			  }

			  if (da > db) {
				return 1;
			  }

			  return 0;
			}
		  }
	  );

	  return events;

	}

	private String makeGadEventId(long time) {

	  SimpleDateFormat idFmt =
		  new SimpleDateFormat("yyMMddHHmm");

	  idFmt.setTimeZone(TimeZone.getTimeZone("UTC"));

	  return idFmt.format(new Date(time));
	}

	private long floorToMinuteUtc(long time) {

	  java.util.Calendar cal =
		  java.util.Calendar.getInstance(
			  TimeZone.getTimeZone("UTC"));

	  cal.setTimeInMillis(time);
	  cal.set(java.util.Calendar.SECOND, 0);
	  cal.set(java.util.Calendar.MILLISECOND, 0);

	  return cal.getTimeInMillis();
	}
	
	private int findAnyColumn(String[] cols, String[] names) {

	  for (int n = 0; n < names.length; n++) {
		int idx = findColumn(cols, names[n]);
		if (idx >= 0) {
		  return idx;
		}
	  }

	  return -1;
	}

	private File getEventCatalogFile(long time) {

	  java.util.Calendar cal =
		  java.util.Calendar.getInstance(
			  TimeZone.getTimeZone("UTC"));

	  cal.setTimeInMillis(time);

	  int year = cal.get(java.util.Calendar.YEAR);
	  int month = cal.get(java.util.Calendar.MONTH) + 1;
	  int day = cal.get(java.util.Calendar.DAY_OF_MONTH);

	  String ymd =
		  String.format(Locale.US, "%04d%02d%02d", year, month, day);

	  return new File(
		  catalogRoot,
		  year + File.separator
			  + ymd + File.separator
			  + "Swarm_EventCatalog_" + ymd + "_ALL.csv"
	  );
	}
	
	private int findColumn(String[] cols, String name) {

	  for (int i = 0; i < cols.length; i++) {
		if (cols[i].trim().equalsIgnoreCase(name)) {
		  return i;
		}
	  }

	  return -1;
	}

	private String getCsvValue(String[] values, int index) {

	  if (index < 0 || index >= values.length) {
		return "";
	  }

	  return values[index].trim();
	}

	private long parseCsvTime(String text) throws java.text.ParseException {

	text = text.trim();

	if (text.matches("\\d+(\\.\\d+)?")) {
	  double value = Double.parseDouble(text);

	  if (value > 1000000000000.0) {
		return (long) value; // epoch milliseconds
	  }

	  return (long) (value * 1000.0); // epoch seconds
	}

	  String[] formats = {
		  "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
		  "yyyy-MM-dd'T'HH:mm:ss'Z'",
		  "yyyy-MM-dd HH:mm:ss.SSS",
		  "yyyy-MM-dd HH:mm:ss"
	  };

	  java.text.ParseException last = null;

	  for (int i = 0; i < formats.length; i++) {
		try {
		  SimpleDateFormat fmt =
			  new SimpleDateFormat(formats[i]);

		  fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

		  return fmt.parse(text).getTime();

		} catch (java.text.ParseException e) {
		  last = e;
		}
	  }

	  throw last;
	}

	private String formatUtc(long time) {

	  SimpleDateFormat fmt =
		  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	  fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

	  return fmt.format(new Date(time));
	}
	
	private long getBaseTimeFromEventId(String eventId) throws java.text.ParseException {

	  String gadEventId = eventId;

	  if (gadEventId.length() > 10) {
		gadEventId = gadEventId.substring(0, 10);
	  }

	  SimpleDateFormat fmt =
		  new SimpleDateFormat("yyMMddHHmm");

	  fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

	  return fmt.parse(gadEventId).getTime();
	}

	private CatalogEvent createEventFromFirstPick(long referencePTime) {

	  CatalogEvent ev = new CatalogEvent();

	  String refStation = getReferenceStationFromClipboard(referencePTime);

	  ev.gadEventId = makeGadEventId(referencePTime);
	  ev.eventId = refStation + "_TEMP_" + ev.gadEventId;
	  ev.eventTime = referencePTime;
	  ev.baseTime = floorToMinuteUtc(referencePTime);
	  ev.station = refStation;
	  ev.classification = "UNLINKED";

	  return ev;
	}

	private String getReferenceStationFromClipboard(long referencePTime) {

	  for (WaveViewPanel wvp :
		  WaveClipboardFrame
			  .getInstance()
			  .getWaves()) {

		PickData pd = wvp.getPickData();

		Pick pPick = pd.getPick(PickData.P);

		if (pPick == null) {
		  continue;
		}

		if (Math.abs(pPick.getTime() - referencePTime) < 1L) {
		  return sanitizeEventIdPart(wvp.getChannel());
		}
	  }

	  return "UNKNOWN";
	}

	private String sanitizeEventIdPart(String text) {

	  if (text == null) {
		return "UNKNOWN";
	  }

	  text = text.trim();

	  if (text.length() == 0) {
		return "UNKNOWN";
	  }

	  return text.replaceAll("[^A-Za-z0-9]+", "_");
	}

	public boolean calculateFromClipboard() throws IOException, InterruptedException {

	validateGadFolder();
	ClipboardPickInfo pickInfo = getClipboardPickInfo();

	if (pickInfo.nPick < 3) {
	  throw new IOException("Minimal 3 pick P diperlukan untuk GAD.");
	}

	CatalogEvent selectedEvent =
		selectCatalogEventNearPick(pickInfo.referencePTime);

	if (selectedEvent == null) {
	  resultText = "GAD dibatalkan: event catalog tidak dipilih.";
	  return false;
	}

	writeArrivalDat(selectedEvent.gadEventId, selectedEvent.baseTime);
	ProcessBuilder pb =
		new ProcessBuilder(gadExe.getAbsolutePath());
		
	pb.directory(gadDir);
	pb.redirectErrorStream(true);
	Process p = pb.start();
	StringBuilder console = new StringBuilder();

	try (BufferedReader br =
		new BufferedReader(
			new InputStreamReader(p.getInputStream()))) {
	  String line;
	  while ((line = br.readLine()) != null) {
		console.append(line)
			.append(System.lineSeparator());
	  }
	}

	int code = p.waitFor();

	resultText = console.toString();

	if (resultFile.exists()) {
	  resultText = readFile(resultFile);
	}

	try (FileWriter hist = new FileWriter(resultHistoryFile, true)) {
	  hist.write("====================================\n");
	  hist.write(new java.util.Date().toString() + "\n");
	  hist.write(resultText);
	  hist.write("\n");
	}

	result = parseResult(resultText);

	if (result != null) {
	  result.eventId = selectedEvent.eventId;
	}

	SummitReference summit = readSummitDat();

	if (result != null
		&& result.x != null
		&& result.y != null
		&& result.z != null) {

	  result.latitude =
		  summit.latitude + result.y / 111.32;

	  result.longitude =
		  summit.longitude +
		  result.x /
		  (111.32 *
		  Math.cos(Math.toRadians(
			  summit.latitude)));

		// Nilai asli GAD (referensi MSL)
		result.elevationRelativeKm = result.z;

		// Kedalaman relatif terhadap puncak
		result.depthKm =
			summit.elevationKm + result.z;
	  
	  appendHypocenterCsv();
	}

	System.out.println(
		"GAD WORKDIR = "
		+ gadDir.getAbsolutePath());

	System.out.println(
		"GAD EXIT CODE = "
		+ code);

	System.out.println(
		"GAD RESULT FILE = "
		+ resultFile.getAbsolutePath());

	System.out.println(
		"GAD RESULT EXISTS = "
		+ resultFile.exists());

	return resultText != null
		&& resultText.contains(
			"Travel time residual rms");
	}

	private void validateGadFolder() throws IOException {

	if (!velocityFile.exists()) {
	  throw new IOException("velocity.dat tidak ditemukan: " + velocityFile.getAbsolutePath());
	}

	if (!stationFile.exists()) {
	  throw new IOException("station.dat tidak ditemukan: " + stationFile.getAbsolutePath());
	}

	if (!summitFile.exists()) {
	  throw new IOException("summit.dat tidak ditemukan: " + summitFile.getAbsolutePath());
	}
	}

	private void writeArrivalDat(String eventId, long eventBaseTime) throws IOException {

	  StringBuilder sb = new StringBuilder();

	  int nPick = 0;

	  for (WaveViewPanel wvp :
		  WaveClipboardFrame
			  .getInstance()
			  .getWaves()) {

		PickData pd = wvp.getPickData();

		Pick pPick = pd.getPick(PickData.P);
		Pick sPick = pd.getPick(PickData.S);

		if (pPick == null) {
		  continue;
		}

		String station =
			extractStationCode(
				wvp.getChannel());

		station = normalizeStationForGad(station);

		double pSec =
			(pPick.getTime() - eventBaseTime) / 1000.0;

		String pPolarity = getPickPolarity(pPick);
		String pQuality = getPickQuality(pPick);

		double sSec = 99.990;
		String sQuality = "E";

		if (sPick != null) {
		  sSec =
			  (sPick.getTime() - eventBaseTime) / 1000.0;

		  sQuality = getPickQuality(sPick);

		  if (sSec < pSec) {
			sSec = 99.990;
			sQuality = "E";
		  }
		}

		sb.append(String.format(
			Locale.US,
			"%s,%s,%.3f,%s,%s,%.3f,%s%n",
			eventId,
			station,
			pSec,
			pPolarity,
			pQuality,
			sSec,
			sQuality
		));

		nPick++;
	  }

	  if (nPick < 3) {
		throw new IOException("Minimal 3 pick P diperlukan untuk GAD.");
	  }

	  sb.append(System.lineSeparator());

	  sb.append("9999999999")
		  .append(System.lineSeparator());

	  try (FileWriter fw =
		  new FileWriter(arrivalFile, false)) {

		fw.write(sb.toString());
	  }

	  try (FileWriter hist =
		  new FileWriter(arrivalHistoryFile, true)) {

		hist.write(
			"====================================\n");

		hist.write(
			new java.util.Date().toString()
				+ "\n");

		hist.write(sb.toString());

		hist.write("\n");
	  }
	}

	private String normalizeStationForGad(String station) {
	  if (station == null) {
		return "    ";
	  }

	  station = station.trim();

	  if (station.length() >= 4) {
		return station.substring(0, 4);
	  }

	  return String.format("%-4s", station);
	}

	private String extractStationCode(
	  String channelName) {

	if (channelName == null) {
	  return "";
	}
	channelName = channelName.trim();
	String[] parts =
		channelName.split("\\s+");
	if (parts.length > 0) {
	  return parts[0].trim();
	}
	return channelName;
	}

	private String getPickPolarity(Pick pick) {

	  if (pick == null) {
		return "E";
	  }

	  String polarity = String.valueOf(pick.getPolarity());

	  if (polarity == null) {
		return "+";
	  }

	  polarity = polarity.toLowerCase();

	  if (polarity.contains("negative")
		  || polarity.contains("down")
		  || polarity.equals("-")) {
		return "-";
	  }

	  if (polarity.contains("positive")
		  || polarity.contains("up")
		  || polarity.equals("+")) {
		return "+";
	  }

	  return "+";
	}

	private String getPickQuality(Pick pick) {

	  if (pick == null) {
		return "E";
	  }

	  String onset = String.valueOf(pick.getOnset());

	  if (onset == null) {
		return "E";
	  }

	  onset = onset.trim().toLowerCase();

	  if (onset.contains("impulsive")
		  || onset.equals("i")) {
		return "I";
	  }

	  if (onset.contains("emergent")
		  || onset.equals("e")) {
		return "E";
	  }

	  return "E";
	}

	private static class ClipboardPickInfo {
	  long referencePTime;
	  int nPick;
	}

	private static class CatalogEvent {
	  String eventId;      // eventId asli dari EventCatalog
	  String gadEventId;   // eventId pendek untuk Arrival.dat
	  long eventTime;
	  long baseTime;
	  String station;
	  String classification;
	}

	private ClipboardPickInfo getClipboardPickInfo() {

	  ClipboardPickInfo info = new ClipboardPickInfo();

	  info.referencePTime = Long.MAX_VALUE;
	  info.nPick = 0;

	  for (WaveViewPanel wvp :
		  WaveClipboardFrame
			  .getInstance()
			  .getWaves()) {

		PickData pd = wvp.getPickData();

		Pick pPick = pd.getPick(PickData.P);

		if (pPick == null) {
		  continue;
		}

		info.nPick++;

		if (pPick.getTime() < info.referencePTime) {
		  info.referencePTime = pPick.getTime();
		}
	  }

	  if (info.referencePTime == Long.MAX_VALUE) {
		info.referencePTime = 0L;
	  }

	  return info;
	}

	private SummitReference readSummitDat() throws IOException {

	File summitFile = new File(gadDir, "summit.dat");

	try (BufferedReader br =
		new BufferedReader(
			new FileReader(summitFile))) {
	  String line;

	  while ((line = br.readLine()) != null) {
		line = line.trim();

		if (line.isEmpty()
			|| line.startsWith("#")) {
		  continue;
		}

		String[] p = line.split("\\s+");

		SummitReference s =
			new SummitReference();
		s.name = p[0];

		s.latitude =
			Double.parseDouble(p[1]);

		s.longitude =
			Double.parseDouble(p[2]);

		s.elevationKm =
			Double.parseDouble(p[3]);

		return s;
	  }
	}

	throw new IOException(
		"Isi summit.dat tidak valid.");
	}

	private GadResult parseResult(String text) {
	GadResult r = new GadResult();

	r.x = findDouble("\\bX\\s+([-+.0-9]+)", text);
	r.y = findDouble("\\bY\\s+([-+.0-9]+)", text);
	r.z = findDouble("\\bZ\\s+([-+.0-9]+)", text);
	r.t = findDouble("\\bT\\s+([-+.0-9]+)", text);

	r.originTime = parseOriginTime(text, r.t);

	r.rms =
		findDouble(
			"rms=\\s*([-+.0-9]+)sec",
			text);

	return r;
	}

	private Double findDouble(
	  String regex,
	  String text) {

	Pattern p = Pattern.compile(regex);

	Matcher m = p.matcher(text);

	if (m.find()) {
	  return Double.parseDouble(
		  m.group(1));
	}

	return null;
	}

	private String parseOriginTime(
		String text,
		Double sec) {

	  Pattern p = Pattern.compile(
		  "Date\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+Time\\s+(\\d+)\\s*:\\s*(\\d+)");

	  Matcher m = p.matcher(text);

	  if (m.find() && sec != null) {

		int yy = Integer.parseInt(m.group(1));
		int mm = Integer.parseInt(m.group(2));
		int dd = Integer.parseInt(m.group(3));
		int hh = Integer.parseInt(m.group(4));
		int min = Integer.parseInt(m.group(5));

		int year = 2000 + yy;

		return String.format(
			Locale.US,
			"%04d-%02d-%02d %02d:%02d:%06.3f",
			year,
			mm,
			dd,
			hh,
			min,
			sec
		);
	  }

	  return "";
	}

	private String readFile(File f) throws IOException {

	StringBuilder sb =
		new StringBuilder();

	try (BufferedReader br =
		new BufferedReader(
			new FileReader(f))) {

	  String line;

	  while ((line = br.readLine()) != null) {
		sb.append(line)
			.append(System.lineSeparator());
	  }
	}

	return sb.toString();
	}

	private void appendHypocenterCsv() throws IOException {

	  boolean writeHeader =
		  !hypocenterCsvFile.exists();

	  try (FileWriter fw =
		  new FileWriter(hypocenterCsvFile, true)) {

		if (writeHeader) {

		  fw.write(
			  "eventId,time,latitude,longitude,"
			  + "depth_below_summit_km,"
			  + "elevation_msl_km,"
			  + "rms,t_sec,"
			  + "x_km,y_km,z_km\n");
		}

		fw.write(String.format(
			Locale.US,
			"%s,%s,%.6f,%.6f,%.3f,%.3f,%.3f,%.3f,"
			+ "%.3f,%.3f,%.3f%n",

			result.eventId,
			result.originTime,
			result.latitude,
			result.longitude,

			result.depthKm,
			result.elevationRelativeKm,

			result.rms,
			result.t,
			result.x,
			result.y,
			result.z
		));
	  }
	}

	public String getResultText() {
	return resultText;
	}

	public GadResult getResult() {
	return result;
	}

	public File getResultFile() {
	return resultFile;
	}

	public static class SummitReference {
	  
	public String name;
	public double latitude;
	public double longitude;
	public double elevationKm;
	}

	public static class GadResult {
	  
	public Double x;
	public Double y;
	public Double z;
	public Double t;
	public Double rms;

	public Double latitude;
	public Double longitude;
	public Double depthKm;              // depth below summit
	public Double elevationRelativeKm; // elevasi terhadap MSL (nilai Z asli)
	public String eventId;

	public String originTime;
	}
}