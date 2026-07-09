# Swarm

Swarm is a Java desktop application for displaying, picking, and analyzing seismic waveforms in real time. It can read static and dynamic waveform sources including Earthworm and Winston wave servers, SeedLink, FDSN web services, and waveform files. Swarm includes helicorder and wave views, time-domain and frequency-domain tools, event picking, mapping, kiosk-style monitoring, and catalog/statistics tools.

This repository is a PVMBG-oriented Swarm 3.2.1 fork with additional event tagger, catalog, coupled-event, and seismicity-statistics workflows.

## Project Info

- Group/artifact: `gov.usgs.volcanoes:swarm`
- Version: `3.2.1-PVMBG`
- Language: Java
- Build system: Maven
- Java target: Java 8
- Main upstream project: <https://code.usgs.gov/vsc/swarm>
- USGS software page: <https://volcanoes.usgs.gov/software/swarm>

## Key Features

- Real-time helicorder and waveform display.
- Waveform access from Winston, Earthworm, SeedLink, FDSN, SEED/SAC, and local files.
- Phase picking, event classification, event notes, and operator-tagged tagger CSV files.
- Duration magnitude, amplitude, dominant-frequency, and half-power frequency-band catalog fields.
- Configurable classification behavior through `EventClassifications.config`.
- Separate coupled-event picking and catalog output.
- Catalog rebuild, validation, export, and seismicity statistics tools.
- Mapping, RSAM, Hypo71, QuakeML, GAD, and waveform clipboard workflows.

## Getting Started

Prerequisites:

- Java 8 or newer.
- Maven.
- Network access to Maven repositories the first time dependencies are resolved.

Build the project:

```powershell
mvn -DskipTests compile
mvn -DskipTests package
```

On Windows, Swarm can also be launched through:

```powershell
.\swarm.bat
```

Console launch:

```powershell
.\swarm_console.bat
```

On Unix-like systems:

```sh
./swarm.sh
```

## Configuration Files

Common root-level configuration files include:

- `EventClassifications.config`: event classification labels, colors, and optional behavior flags.
- `SwarmGroups.config`: channel/group definitions.
- `SwarmMetadata.config`: station metadata used by tools such as statistics scaling.
- `WaveDefaults.config`: default wave-view settings.
- `RsamDefaults.config`: RSAM defaults.
- `Hypo71.config`: Hypo71 settings.
- `TaggerDirectory.config`: saved root directory for SwarmTagger outputs.

### Event Classification Flags

`EventClassifications.config` supports backward-compatible rows:

```text
VT,#fc7703
```

Rows may also include optional behavior flags:

```text
Lahar,#5481a8,skipMdFrequency
Tektonik Jauh,#000080,skipMdFrequency
Tremor Menerus,#FF00FF,skipMdFrequency
```

`skipMdFrequency` means:

- keep duration and amplitude fields,
- leave `Md` blank,
- skip dominant frequency and `f1/f2/f3` for normal event catalog output.

The flag is explicit only. Swarm does not infer aliases such as translated classification names.

## SwarmTagger Workflow

The helicorder tag menu writes tagger CSV files under the configured tagger root:

```text
tagger/YYYY/YYYYMMDD/STATION/
```

Normal event tagger files use:

```text
SwarmTagger_YYYYMMDD_STATION_OPERATOR.csv
```

Normal events are grouped by `eventId`, selected classification, and phase picks such as `P`, `S`, and `END`.

## Coupled Events

The tag menu includes a `Coupled Event` submenu with:

- `P1`
- `P2`
- `P3`
- `END`
- coupled pick deletion
- coupled event deletion

Picking order is enforced:

```text
P1 -> P2 -> optional P3 -> END
```

`END` marks the end of the whole coupled event. Coupled events still use the normal event classification selected by the user, but their note is automatically set to:

```text
Coupled Events
```

Coupled tagger files are separate from normal tagger files:

```text
SwarmTaggerCoupled_YYYYMMDD_STA_CHA_NET_LOC_OPERATOR.csv
```

Example:

```text
SwarmTaggerCoupled_20260708_BTP_EHZ_VG_00_ASUS.csv
```

Deleting a coupled event removes the classification tag and all coupled picks for that `eventId`.

## Catalog Output

Catalog rebuild writes normal event catalogs under:

```text
catalog/YYYY/YYYYMMDD/
```

Normal catalog files:

```text
Swarm_EventCatalog_YYYYMMDD_ALL.csv
station/Swarm_EventCatalog_YYYYMMDD_STATION.csv
```

If coupled tagger files exist for the day, rebuild also writes coupled catalogs:

```text
Swarm_CoupledEventCatalog_YYYYMMDD_ALL.csv
station/Swarm_CoupledEventCatalog_YYYYMMDD_STA_CHA_NET_LOC.csv
```

Coupled catalog rows include:

- `P1`, `P2`, optional `P3`, and `END` times,
- `P2-P1`, `P3-P2`, `END-P1`, and `END-P2` durations,
- peak-to-peak amplitudes for coupled windows,
- amplitude ratio,
- dominant frequency and `f1/f2/f3` fields for coupled windows,
- selected classification,
- automatic note `Coupled Events`.

Coupled events are not written into the normal event catalog.

## Seismicity Statistics

`Tools -> Seismicity Statistics` reads catalog CSV files and summarizes events by classification and note.

For normal catalog rows, statistics use the normal catalog amplitude and duration mapping.

For coupled catalog rows, statistics use:

- time: `P1`
- amplitude: `peakToPeakP2P1`
- duration: `END - P1`
- event type: selected classification
- note: `Coupled Events`

The statistics table includes a `Note` column so normal and coupled rows remain distinct even when they share the same classification.

## Project Structure

Important folders:

- `src/main/java/gov/usgs/volcanoes/swarm`: main application source.
- `src/main/java/gov/usgs/volcanoes/swarm/event`: event picking, tagger, catalog, Hypo71, GAD, and event-related tools.
- `src/main/java/gov/usgs/volcanoes/swarm/heli`: helicorder views.
- `src/main/java/gov/usgs/volcanoes/swarm/wave`: wave views and waveform interaction.
- `src/main/java/gov/usgs/volcanoes/swarm/statistics`: catalog statistics and seismicity summaries.
- `docs`: user and technical documentation.
- `layouts`, `mapdata`, `GAD`: runtime support data.

## Development

Use Maven for normal compile/package verification:

```powershell
mvn -DskipTests compile
```

The codebase uses Java Swing for desktop UI and follows the existing package-level organization. Keep event tagger and catalog behavior centralized where possible so manual picking, rebuild, validation, and statistics stay consistent.

## Documentation

Additional documentation is available in:

- `docs/Swarm User Guide.docx`
- `docs/swarm_v3.pdf`
- `docs/hypo71manual.pdf`
- `CHANGES.md`

## License and Attribution

Swarm is open source and public domain under CC0 1.0 Universal. See `LICENSE.md` and `DISCLAIMER.md`.

Swarm was originally developed by Dan Cervelli and Peter Cervelli for the USGS Volcano Science Center. Later maintenance and enhancements were made by Tom Parker and Diana Norgaard, with contributions from Loren Antolik, Kevin Frechette, Anthony Lomax, Dave Ketchum, Chirag Patel, Ivan Henson, and others.

DOI: <https://doi.org/10.5066/P93A9MWK>
