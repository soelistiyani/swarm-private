package gov.usgs.volcanoes.swarm.data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class SdsScanner {

  public static class SdsChannel {

    public String network;
    public String station;
    public String location;
    public String channel;

    public SdsChannel(
        String network,
        String station,
        String location,
        String channel) {

      this.network = network;
      this.station = station;
      this.location = location;
      this.channel = channel;
    }

    @Override
    public String toString() {
      if (location == null || location.length() == 0) {
        return network + "." + station + ".." + channel;
      }

      return network + "." + station + "." + location + "." + channel;
    }
  }

  public static List<SdsChannel> scan(File sdsRoot) {

    List<SdsChannel> result = new ArrayList<>();
	
	Set<String> seen = new HashSet<String>();

    File[] years = sdsRoot.listFiles(File::isDirectory);

    if (years == null) {
      return result;
    }

    for (File yearDir : years) {

      File[] nets = yearDir.listFiles(File::isDirectory);

      if (nets == null) {
        continue;
      }

      for (File netDir : nets) {

        File[] stations = netDir.listFiles(File::isDirectory);

        if (stations == null) {
          continue;
        }

        for (File staDir : stations) {

          File[] channels = staDir.listFiles(File::isDirectory);

          if (channels == null) {
            continue;
          }

          for (File chaDir : channels) {

            String chaName = chaDir.getName();

            if (!chaName.endsWith(".D")) {
              continue;
            }

            String channel =
                chaName.substring(0, chaName.length() - 2);

            File[] dataFiles = chaDir.listFiles(File::isFile);

            if (dataFiles == null) {
              continue;
            }

            for (File dataFile : dataFiles) {

              String filename = dataFile.getName();

              String prefix =
                  netDir.getName() + "." + staDir.getName() + ".";

              if (!filename.startsWith(prefix)) {
                continue;
              }

              String[] parts = filename.split("\\.");

              if (parts.length < 7) {
                continue;
              }

              String location = parts[2];

              String key =
                netDir.getName() + "."
                	+ staDir.getName() + "."
                	+ location + "."
                	+ channel;

              if (!seen.contains(key)) {
				  seen.add(key);

				  result.add(
                  new SdsChannel(
                	  netDir.getName(),
                	  staDir.getName(),
                	  location,
                	  channel));
              }

              break;
            }
          }
        }
      }
    }

    return result;
  }
}