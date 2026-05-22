package gov.usgs.volcanoes.swarm.data;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

public class SdsWaveSource {

    private final SdsFileResolver resolver;

    public SdsWaveSource(File sdsRoot) {
        this.resolver = new SdsFileResolver(sdsRoot);
    }

    public List<File> getMiniSeedFiles(
            String network,
            String station,
            String location,
            String channel,
            LocalDateTime startUtc,
            LocalDateTime endUtc) {

        return resolver.resolve(
                network,
                station,
                location,
                channel,
                startUtc,
                endUtc
        );
    }
}