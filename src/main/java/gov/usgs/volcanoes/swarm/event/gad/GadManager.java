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
  
 
  public boolean calculateFromClipboard()
      throws IOException, InterruptedException {

    validateGadFolder();
    writeArrivalDat();
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

	try (FileWriter hist = new FileWriter(resultHistoryFile, true)) {
	  hist.write("====================================\n");
	  hist.write(new java.util.Date().toString() + "\n");
	  hist.write(resultText);
	  hist.write("\n");
	}

	result = parseResult(resultText);
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
        "GAD RESULT EXISTS = "
        + resultFile.exists());

    return resultText != null
        && resultText.contains(
            "Travel time residual rms");
  }

  private void validateGadFolder()
      throws IOException {

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

	private void writeArrivalDat()
		throws IOException {

	  SimpleDateFormat idFmt =
		  new SimpleDateFormat("yyMMddHHmm");

	  idFmt.setTimeZone(
		  TimeZone.getTimeZone("UTC"));

	  StringBuilder sb = new StringBuilder();

	  String eventId = null;
	  long eventBaseTime = 0L;

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

		if (eventId == null) {
		  Date firstPickDate =
			  new Date(pPick.getTime());

		  eventId =
			  idFmt.format(firstPickDate);

		  java.util.Calendar cal =
			  java.util.Calendar.getInstance(
				  TimeZone.getTimeZone("UTC"));

		  cal.setTime(firstPickDate);
		  cal.set(java.util.Calendar.SECOND, 0);
		  cal.set(java.util.Calendar.MILLISECOND, 0);

		  eventBaseTime =
			  cal.getTimeInMillis();
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
		  sSec = (sPick.getTime() - eventBaseTime) / 1000.0;
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


  private SummitReference readSummitDat()
      throws IOException {

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

  private GadResult parseResult(String text) 
  {
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
		  "Date\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+Time\\s+(\\d+):(\\d+)");

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


  private String readFile(File f)
      throws IOException {

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

	private void appendHypocenterCsv()
		throws IOException {

	  boolean writeHeader =
		  !hypocenterCsvFile.exists();

	  try (FileWriter fw =
		  new FileWriter(hypocenterCsvFile, true)) {

		if (writeHeader) {
			
		fw.write(
			"time,latitude,longitude,"
			+ "depth_below_summit_km,"
			+ "elevation_msl_km,"
			+ "rms,"
			+ "x_km,y_km,z_km\n");
			
		}

		fw.write(String.format(
			Locale.US,
			"%s,%.6f,%.6f,%.3f,%.3f,%.3f,"
			+ "%.3f,%.3f,%.3f%n",

			result.originTime,
			result.latitude,
			result.longitude,
			
			result.depthKm,
			result.elevationRelativeKm,
			
			result.rms,
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
	
	public String originTime;
  }
}