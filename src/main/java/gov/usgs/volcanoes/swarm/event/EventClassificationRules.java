package gov.usgs.volcanoes.swarm.event;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EventClassificationRules {

	private static final String SETTINGS_FILE_NAME = "EventClassifications.config";
	private static final String SKIP_MD_FREQUENCY_FLAG = "skipmdfrequency";

	private static final Map<String, Set<String>> flagsByClassification =
			new HashMap<String, Set<String>>();

	static {
		loadFlags();
	}

	private static void loadFlags() {
		try {
			FileReader fr = new FileReader(SETTINGS_FILE_NAME);
			BufferedReader br = new BufferedReader(fr);

			String line;
			while ((line = br.readLine()) != null) {
				if (line.trim().length() == 0 || line.trim().startsWith("#")) {
					continue;
				}

				String[] parts = line.trim().split(",");

				if (parts.length < 3) {
					continue;
				}

				String classification = parts[0].trim();
				Set<String> flags = new HashSet<String>();

				for (int i = 2; i < parts.length; i++) {
					String flag = parts[i].trim().toLowerCase();

					if (flag.length() > 0) {
						flags.add(flag);
					}
				}

				flagsByClassification.put(classification, flags);
			}

			br.close();
			fr.close();

		} catch (IOException e) {
			// Old installs may not have an EventClassifications.config file.
		}
	}

	public static boolean skipsDurationMagnitudeAndFrequency(String classification) {
		return hasFlag(classification, SKIP_MD_FREQUENCY_FLAG);
	}

	private static boolean hasFlag(String classification, String flag) {
		if (classification == null) {
			return false;
		}

		Set<String> flags = flagsByClassification.get(classification.trim());

		if (flags == null) {
			return false;
		}

		return flags.contains(flag);
	}
}
