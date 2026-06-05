package gov.usgs.volcanoes.swarm.event;

import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.time.Time;

import java.awt.Color;
import java.text.ParseException;
import java.util.Comparator;

/**
 * Tag data for helicorder event classification and phase picking.
 *
 * CSV format:
 * time,channel,classification,phase,onset,polarity,uncertaintyMs,eventId,operator,note
 *
 * Backward compatible with old format:
 * time,channel,classification
 */
public class TagData implements Comparator<TagData> {

    public String channel;
    public double startTime;
    public String classification;
    public Color color;

    public String phase = "";
    public String onset = "";
    public String polarity = "";
    public int uncertaintyMs = 1;
    public String eventId = "";
    public String operator = "";
    public String note = "";

	public double durationSec = Double.NaN;
	public double maxAbsAmplitude = Double.NaN;
	public String maxAbsTime = "";
	public double peakToPeakAmplitude = Double.NaN;
	public double dominantFrequencyHz = Double.NaN;

	private static final String TIME_FORMAT_MS = "yyyy-MM-dd HH:mm:ss.SSS";

    public TagData() {}

    public TagData(String line) throws ParseException {
        parse(line);
    }

    public TagData(String channel, double startTime, String classification) {
        this.channel = channel;
        this.startTime = startTime;
        this.classification = classification;
        this.eventId = makeEventId(channel, startTime);
        setColor();
    }

    public TagData(
            String channel,
            double startTime,
            String classification,
            String phase,
            String onset,
            String polarity,
            int uncertaintyMs,
            String eventId,
            String operator,
            String note) {

        this.channel = channel;
        this.startTime = startTime;
        this.classification = safe(classification);
        this.phase = safe(phase);
        this.onset = safe(onset);
        this.polarity = safe(polarity);
        this.uncertaintyMs = uncertaintyMs;
        this.eventId = safe(eventId);
        this.operator = safe(operator);
        this.note = safe(note);

        if (this.eventId.length() == 0) {
            this.eventId = makeEventId(channel, startTime);
        }

        setColor();
    }

    private void parse(String line) throws ParseException {
        String[] data = line.split(",", -1);

		try {
			startTime = J2kSec.parse(TIME_FORMAT_MS, data[0].trim());
		} catch (ParseException e) {
			startTime = J2kSec.parse(Time.STANDARD_TIME_FORMAT, data[0].trim());
		}
        channel = data.length > 1 ? data[1].trim() : "";
        classification = data.length > 2 ? data[2].trim() : "Unclassified";

        if (data.length > 3) {
            phase = data[3].trim();
        }
        if (data.length > 4) {
            onset = data[4].trim();
        }
        if (data.length > 5) {
            polarity = data[5].trim();
        }
        if (data.length > 6 && data[6].trim().length() > 0) {
            try {
                uncertaintyMs = Integer.parseInt(data[6].trim());
            } catch (NumberFormatException e) {
                uncertaintyMs = 1;
            }
        }
		
		if (data.length > 7) {
			eventId = data[7].trim();
		}
		if (data.length > 8) {
			operator = data[8].trim();
		}
		if (data.length > 9) {
			note = data[9].trim();
		}

		/*
		 * NEW AMPLITUDE FIELDS
		 */
		if (data.length > 10 && data[10].trim().length() > 0) {
			durationSec = Double.parseDouble(data[10].trim());
		}

		if (data.length > 11 && data[11].trim().length() > 0) {
			maxAbsAmplitude = Double.parseDouble(data[11].trim());
		}

		if (data.length > 12) {
			maxAbsTime = data[12].trim();
		}

		if (data.length > 13 && data[13].trim().length() > 0) {
			peakToPeakAmplitude = Double.parseDouble(data[13].trim());
		}
		
		if (data.length > 14 && data[14] != null && data[14].trim().length() > 0) {
			dominantFrequencyHz = Double.parseDouble(data[14].trim());
		}

		if (eventId == null || eventId.length() == 0) {
			eventId = makeEventId(channel, startTime);
		}

        if (eventId == null || eventId.length() == 0) {
            eventId = makeEventId(channel, startTime);
        }

        setColor();
    }

    private void setColor() {
        this.color = TagMenu.getColor(classification);
    }

    public boolean isPhasePick() {
        return phase != null && phase.length() > 0;
    }

    public boolean isP() {
        return "P".equalsIgnoreCase(phase);
    }

    public boolean isS() {
        return "S".equalsIgnoreCase(phase);
    }

	public boolean isCoda() {
		return "END".equalsIgnoreCase(phase)
				|| "CODA".equalsIgnoreCase(phase);
	}

	public String getPhaseLabel() {

		if (!isPhasePick()) {
			return classification;
		}

		if (isP()) {

			String label = "P";

			if ("+".equalsIgnoreCase(polarity)
					|| "UP".equalsIgnoreCase(polarity)) {
				label += "+";

			} else if ("-".equalsIgnoreCase(polarity)
					|| "DOWN".equalsIgnoreCase(polarity)) {
				label += "-";
			}

			return label;
		}

		if (isS()) {
			return "S";
		}

		if (isCoda()) {
			return "END";
		}

		return phase;
	}

	public String toString() {
		return J2kSec.format(TIME_FORMAT_MS, startTime)
				+ "," + safeCsv(channel)
				+ "," + safeCsv(classification)
				+ "," + safeCsv(phase)
				+ "," + safeCsv(onset)
				+ "," + safeCsv(polarity)
				+ "," + uncertaintyMs
				+ "," + safeCsv(eventId)
				+ "," + safeCsv(operator)
				+ "," + safeCsv(note)
				+ "," + doubleString(durationSec)
				+ "," + doubleString(maxAbsAmplitude)
				+ "," + safeCsv(maxAbsTime)
				+ "," + doubleString(peakToPeakAmplitude)
				+ "," + doubleString(dominantFrequencyHz);
	}

	public String getTimeString() {
		return J2kSec.format(TIME_FORMAT_MS, startTime);
	}

    public int compare(TagData d1, TagData d2) {
        return Double.compare(d1.startTime, d2.startTime);
    }

    public static String makeEventId(String channel, double startTime) {
        String ch = channel == null ? "UNKNOWN" : channel.replaceAll("[^A-Za-z0-9]", "_");
        long t = Math.round(startTime * 1000.0);
        return ch + "_" + t;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String safeCsv(String s) {
        if (s == null) {
            return "";
        }
        return s.replace(",", ";").replace("\n", " ").replace("\r", " ");
    }
	
		private static String doubleString(double d) {
		if (Double.isNaN(d)) {
			return "";
		}
		return Double.toString(d);
	}
	
}