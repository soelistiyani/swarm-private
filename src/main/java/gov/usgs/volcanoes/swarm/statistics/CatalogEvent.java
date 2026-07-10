package gov.usgs.volcanoes.swarm.statistics;

import java.time.LocalDateTime;

public class CatalogEvent {

    public LocalDateTime time;
    public String station;
	public String channel;
    public String eventType;
	public String note;
	public String mmi;
	public String audible;
	public String visible;
    public double amplitude;
    public double duration;
	public double amplitudeUmPerSec;

	public CatalogEvent(
		LocalDateTime time,
		String station,
		String channel,
		String eventType,
		String note,
		double amplitude,
		double duration) {
		this(time, station, channel, eventType, note, "", "", "", amplitude, duration);
	}

	public CatalogEvent(
		LocalDateTime time,
		String station,
		String channel,
		String eventType,
		String note,
		String mmi,
		String audible,
		String visible,
		double amplitude,
		double duration) {

		this.time = time;
		this.station = station;
		this.channel = channel;
		this.eventType = eventType;
		this.note = note;
		this.mmi = mmi;
		this.audible = audible;
		this.visible = visible;
		this.amplitude = amplitude;
		this.duration = duration;
		this.amplitudeUmPerSec = amplitude;
	}
}
