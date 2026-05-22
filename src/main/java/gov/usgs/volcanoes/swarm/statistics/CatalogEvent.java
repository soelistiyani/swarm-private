package gov.usgs.volcanoes.swarm.statistics;

import java.time.LocalDateTime;

public class CatalogEvent {

    public LocalDateTime time;
    public String station;
    public String eventType;
    public double amplitude;
    public double duration;

    public CatalogEvent(
            LocalDateTime time,
            String station,
            String eventType,
            double amplitude,
            double duration) {

        this.time = time;
        this.station = station;
        this.eventType = eventType;
        this.amplitude = amplitude;
        this.duration = duration;
    }
}