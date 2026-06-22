/**
 * Helper
 * to
 * calculate
 * Duration
 * Magnitude.
 * 
 * @author
 * Sulistiyani
 *
 */
 
package gov.usgs.volcanoes.swarm.event;

import gov.usgs.volcanoes.swarm.SwarmConfig;
import java.util.Locale;

public class DurationMagnitudeUtil {
    public static Double calculate(double durationSec) {
        if (Double.isNaN(durationSec) || Double.isInfinite(durationSec) || durationSec <= 0) {
            return null;
        }

        SwarmConfig swarmConfig = SwarmConfig.getInstance();

        if (!swarmConfig.durationEnabled) {
            return null;
        }
        return swarmConfig.durationA * Math.log10(durationSec)
            + swarmConfig.durationB;
    }

    public static String format(Double md) {
        if (md == null || Double.isNaN(md) || Double.isInfinite(md)) {
            return "";
        }
        return String.format(Locale.US, "%.2f", md);
    }
} 
