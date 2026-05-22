package gov.usgs.volcanoes.swarm.data;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class SdsFileResolver {

  private final File sdsRoot;

  public SdsFileResolver(File sdsRoot) {
    this.sdsRoot = sdsRoot;
  }

  public List<File> resolve(
      String network,
      String station,
      String location,
      String channel,
      LocalDateTime startUtc,
      LocalDateTime endUtc) {

    List<File> files = new ArrayList<>();

    ZonedDateTime day =
        startUtc.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC);

    ZonedDateTime lastDay =
        endUtc.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC);

    while (!day.isAfter(lastDay)) {

      int year = day.getYear();
      String doy = String.format("%03d", day.getDayOfYear());

      String loc = location == null ? "" : location.trim();

      String filename = String.format(
          "%s.%s.%s.%s.D.%d.%s",
          network,
          station,
          loc,
          channel,
          year,
          doy
      );

      File file = new File(
          sdsRoot,
          year + File.separator
              + network + File.separator
              + station + File.separator
              + channel + ".D" + File.separator
              + filename
      );

      System.out.println("[SDS CHECK] " + file.getAbsolutePath());

      if (file.exists() && file.isFile()) {
          System.out.println("[SDS FOUND] " + file.getAbsolutePath());
		  files.add(file);
      } else {
		  System.out.println("[SDS MISSING] " + file.getAbsolutePath());
      }
		
      day = day.plusDays(1);
    }

    return files;
  }
}