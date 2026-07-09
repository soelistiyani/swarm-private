package gov.usgs.volcanoes.swarm.statistics;

import java.time.LocalDateTime;

public class CatalogEvent {

    public LocalDateTime time;
    public String station;
	public String channel;
    public String eventType;
	public String note;
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

		this.time = time;
		this.station = station;
		this.channel = channel;
		this.eventType = eventType;
		this.note = note;
		this.amplitude = amplitude;
		this.duration = duration;
		this.amplitudeUmPerSec = amplitude;
	}
}
