package gov.usgs.volcanoes.swarm.statistics;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class MetadataMultiplierReader {

    private static final Pattern MULTIPLIER_PATTERN =
        Pattern.compile("Multiplier\\s*:\\s*([0-9.Ee+-]+)");

    public static Map<String, Double> readMultipliers(File metadataFile)
            throws IOException {

        Map<String, Double> multipliers = new HashMap<String, Double>();

        if (metadataFile == null || !metadataFile.exists()) {
            return multipliers;
        }

        BufferedReader br = new BufferedReader(new FileReader(metadataFile));

        String line;

        while ((line = br.readLine()) != null) {

            line = line.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int eq = line.indexOf("=");

            if (eq < 0) {
                continue;
            }

            String channelKey = line.substring(0, eq).trim();

            Matcher matcher = MULTIPLIER_PATTERN.matcher(line);

            if (!matcher.find()) {
                continue;
            }

            double multiplier =
                Double.parseDouble(matcher.group(1));

            multipliers.put(channelKey, multiplier);

            // tambahan bentuk key alternatif:
            // PPNK HHZ VG 00 -> PPNK_HHZ_VG_00
            multipliers.put(channelKey.replace(" ", "_"), multiplier);
        }

        br.close();

        return multipliers;
    }

    public static double getMultiplier(
            Map<String, Double> multipliers,
            String key) {

        if (multipliers == null || key == null) {
            return 1.0;
        }

        Double m = multipliers.get(key.trim());

        if (m != null) {
            return m.doubleValue();
        }

        m = multipliers.get(key.trim().replace("_", " "));

        if (m != null) {
            return m.doubleValue();
        }

        return 1.0;
    }
}