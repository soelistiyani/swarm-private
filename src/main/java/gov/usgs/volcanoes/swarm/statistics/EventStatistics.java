package gov.usgs.volcanoes.swarm.statistics;

public class EventStatistics {

    public String eventType;

    public int count = 0;

    public double minAmplitude = Double.MAX_VALUE;
    public double maxAmplitude = -Double.MAX_VALUE;
	
	public double minAmplitudeUmPerSec = Double.MAX_VALUE;
	public double maxAmplitudeUmPerSec = -Double.MAX_VALUE;

    public double minDuration = Double.MAX_VALUE;
    public double maxDuration = -Double.MAX_VALUE;

    public EventStatistics(String eventType) {
        this.eventType = eventType;
    }

    public void add(CatalogEvent event) {

        count++;

        if (!Double.isNaN(event.amplitude)) {
            minAmplitude = Math.min(minAmplitude, event.amplitude);
            maxAmplitude = Math.max(maxAmplitude, event.amplitude);
        }

        if (!Double.isNaN(event.duration)) {
            minDuration = Math.min(minDuration, event.duration);
            maxDuration = Math.max(maxDuration, event.duration);
        }
		
		if (!Double.isNaN(event.amplitudeUmPerSec)) {
		    minAmplitudeUmPerSec =
		        Math.min(minAmplitudeUmPerSec, event.amplitudeUmPerSec);

		    maxAmplitudeUmPerSec =
		        Math.max(maxAmplitudeUmPerSec, event.amplitudeUmPerSec);
		}
    }

	public Object[] toTableRow() {

		return new Object[] {
			eventType,
			count,
			format(minAmplitude),
			format(maxAmplitude),
			format(minAmplitudeUmPerSec),
			format(maxAmplitudeUmPerSec),
			format(minDuration),
			format(maxDuration)
		};
	}

    private String format(double value) {

        if (value == Double.MAX_VALUE || value == -Double.MAX_VALUE || Double.isNaN(value)) {
            return "-";
        }

        return String.format("%.2f", value);
    }
}