# Swarm PVMBG

Swarm is a Java desktop application for real-time seismic waveform display, picking, and analysis. It supports helicorder and waveform views, time-domain and frequency-domain analysis, event tagging, catalog rebuilds, mapping, RSAM monitoring, Hypo71, QuakeML, and seismicity statistics.

This repository is a PVMBG-oriented fork of Swarm `3.2.1` with additional workflows for SwarmTagger event classification, duration/amplitude/frequency catalogs, coupled events, and statistics reporting.

## Contents

- [Project Info](#project-info)
- [Features](#features)
- [Build and Run](#build-and-run)
- [Configuration](#configuration)
- [Event Tagging](#event-tagging)
- [Coupled Events](#coupled-events)
- [Catalogs](#catalogs)
- [GAD Hypocenter Calculation](#gad-hypocenter-calculation)
- [Seismicity Statistics](#seismicity-statistics)
- [Project Structure](#project-structure)
- [Development Notes](#development-notes)
- [License and Attribution](#license-and-attribution)

## Project Info

| Item | Value |
| --- | --- |
| Maven artifact | `gov.usgs.volcanoes:swarm` |
| Version | `3.2.1-PVMBG` |
| Language | Java |
| UI toolkit | Swing |
| Build tool | Maven |
| Java target | Java 8 |
| Upstream repository | <https://code.usgs.gov/vsc/swarm> |
| USGS software page | <https://volcanoes.usgs.gov/software/swarm> |

## Features

Core Swarm features:

- Real-time helicorder and waveform viewing.
- Waveform data access from Winston, Earthworm, SeedLink, FDSN web services, SEED/SAC, ASCII, and local files.
- Time-domain and frequency-domain waveform tools.
- Particle-motion view with incidence angle and azimuth shown in the plot information area.
- Event picking, QuakeML workflows, Hypo71 support, GAD support, mapping, RSAM, and kiosk-style monitoring.

PVMBG-oriented additions in this fork:

- Event classifications loaded from `EventClassifications.config`.
- SwarmTagger CSV output grouped by operator, day, station, and event ID.
- Structured event observations for `mmi`, `audible`, and `visible` catalog fields.
- Catalog rebuild and validation from tagger CSV files.
- Optional classification flags such as `skipMdFrequency` and `allowLongDuration`.
- Separate coupled-event picking workflow with `P1`, `P2`, optional `P3`, and `END`.
- Separate coupled-event tagger and catalog files.
- Distinct coupled-event helicorder display using hatched duration blocks and `C-END` labels.
- Seismicity Statistics support for normal and coupled event catalog rows.

## Build and Run

Prerequisites:

- Java 8 or newer.
- Maven.
- Network access for first-time Maven dependency resolution.

Compile:

```powershell
mvn -DskipTests compile
```

Build the packaged JAR:

```powershell
mvn -DskipTests package
```

Run on Windows:

```powershell
.\swarm.bat
```

Run with console output on Windows:

```powershell
.\swarm_console.bat
```

Run on Unix-like systems:

```sh
./swarm.sh
```

## Configuration

Important root-level configuration files:

| File | Purpose |
| --- | --- |
| `EventClassifications.config` | Event labels, display colors, and optional behavior flags. |
| `TaggerDirectory.config` | Saved root directory for SwarmTagger output. |
| `SwarmGroups.config` | Channel/group definitions. |
| `SwarmMetadata.config` | Station metadata, including multipliers used by statistics tools. |
| `WaveDefaults.config` | Default waveform display settings. |
| `RsamDefaults.config` | RSAM defaults. |
| `Hypo71.config` | Hypo71 configuration. |
| `NTP.config` | Time configuration. |

### Event Classification Format

Basic rows remain backward compatible:

```text
VT,#fc7703
```

Rows can include optional behavior flags after the color:

```text
Tremor Menerus,#FF00FF,skipMdFrequency,allowLongDuration
Lahar,#8B4513,skipMdFrequency,allowLongDuration
Tektonik Jauh,#000080,skipMdFrequency,allowLongDuration
Banjir/Lahar,#008080,skipMdFrequency,allowLongDuration
```

Supported flags:

| Flag | Behavior |
| --- | --- |
| `skipMdFrequency` | Keeps duration and amplitude, leaves `Md` blank, and skips dominant frequency plus `f1/f2/f3` for normal event output. |
| `allowLongDuration` | Allows long `P -> END` durations without validation warnings and calculates amplitude from only the first 300 seconds after `P`. |

Flags are explicit. Swarm does not infer translated names or aliases automatically.

## Event Tagging

The helicorder tag menu writes CSV rows under the configured tagger root:

```text
tagger/YYYY/YYYYMMDD/STATION/
```

Normal tagger files use this filename pattern:

```text
SwarmTagger_YYYYMMDD_STATION_OPERATOR.csv
```

Normal event flow:

1. Select an event classification.
2. Pick `P` with onset and polarity.
3. Optionally pick `S`.
4. Pick `END`.
5. Swarm writes the event using a shared `eventId`.

The SwarmTagger `P` phase menu records only onset (`IMPULSIVE` or `EMERGENT`) and polarity (`+`, `-`, or `UNKNOWN`). It does not ask for pick uncertainty because the event catalog does not require that value. Pick uncertainty remains available in the wave inset pick menu for hypocenter calculations.

For normal events, the `END` pick carries duration, amplitude, and frequency fields when those calculations are enabled for the classification.

Normal and coupled pick sequences are mutually exclusive while an event is in progress. After a normal `P` pick, finish normal `END` before starting `Coupled Event`. After coupled `P1`, finish coupled `END` before starting normal phase picks.

### Observations and Notes

The tag menu includes an `Observation` submenu before `Add/Edit Note`. Observations are structured fields saved with the event and written to the normal catalog:

| Observation | Stored field | User input |
| --- | --- | --- |
| `Felt` | `mmi` | Roman numerals `I` through `XII`; default `I`. |
| `Audible` | `audible` | `Soft`, `Moderate`, or `Loud`; default `Soft`. |
| `Visible` | `visible` | Free text, sanitized for CSV output. |

One event can have all three observations. `Add/Edit Note` remains a separate free-text note field for operator comments.

## Coupled Events

The tag menu includes a separate `Coupled Event` submenu. Coupled events still require the user to select a normal event classification first, such as `VT`, `LF`, `Lahar`, or another configured class.

Coupled pick sequence:

```text
P1 -> P2 -> optional P3 -> END
```

Rules:

- `P1` starts the coupled event.
- `P2` requires `P1`.
- `P3` requires `P2` and is optional.
- `END` requires `P2`; if `P3` exists, `END` must be after `P3`.
- `END` marks the end of the whole coupled event.
- Coupled `END` is displayed as `C-END` on the helicorder.
- The note is automatically set to `Coupled Events`.
- Deleting a coupled event removes the classification tag and all coupled picks for that `eventId`.
- Coupled duration windows use the same classification color as normal events, with diagonal hatching to distinguish them.

Coupled tagger files are separate from normal tagger files:

```text
SwarmTaggerCoupled_YYYYMMDD_STA_CHA_NET_LOC_OPERATOR.csv
```

Example:

```text
SwarmTaggerCoupled_20260710_PPKB_HHZ_VG_00_ASUS.csv
```

Internally, coupled phases are stored separately from normal `P/S/END` picks so they do not enter the normal catalog by accident.

When reviewing historical files, `Open existing swarmtagger file` accepts only `SwarmTagger_*.csv` and `SwarmTaggerCoupled_*.csv`. Catalog files such as `Swarm_EventCatalog_*.csv` are rejected. Opening a normal tagger file and then a coupled tagger file keeps both sets of tags visible together.

## Catalogs

Catalog rebuild reads tagger CSV files and writes daily catalog files under:

```text
catalog/YYYY/YYYYMMDD/
```

Normal catalog output:

```text
Swarm_EventCatalog_YYYYMMDD_ALL.csv
station/Swarm_EventCatalog_YYYYMMDD_STATION.csv
```

Coupled catalog output, generated when coupled tagger files exist:

```text
Swarm_CoupledEventCatalog_YYYYMMDD_ALL.csv
station/Swarm_CoupledEventCatalog_YYYYMMDD_STA_CHA_NET_LOC.csv
```

Normal catalog rows include the standard `P`, `S`, `END`, duration, `Md`, amplitude, dominant frequency, frequency-band fields, operator, note, and structured observation fields.

Normal catalog observation columns:

| Column | Meaning |
| --- | --- |
| `mmi` | Felt intensity as `I` through `XII`. Empty when no felt observation was entered. |
| `audible` | Audible intensity: `Soft`, `Moderate`, or `Loud`. Empty when no audible observation was entered. |
| `visible` | Visible observation text. Empty when no visible observation was entered. |

Coupled catalog rows include:

- `P1`, `P2`, optional `P3`, and `END` times.
- `P2-P1`, `P3-P2`, `END-P1`, and `END-P2` durations.
- Peak-to-peak amplitudes for coupled windows.
- Amplitude ratio.
- Dominant frequency and `f1/f2/f3` for coupled windows.
- Selected classification.
- Note value `Coupled Events`.

Coupled events are not written into the normal event catalog.

## GAD Hypocenter Calculation

`Event Dialog -> GAD` runs the GAD executable from the selected GAD folder. The selected folder remains the GAD working directory, so `Arrival.dat`, result files, history files, and runtime outputs are written there.

Required files in the selected GAD folder:

```text
velocity.dat
station.dat
summit.dat
```

GAD executable lookup is OS-specific. The recommended cross-platform folder layout is:

```text
GAD/
  windows/GAD.exe
  linux/GAD
  macos/GAD
  velocity.dat
  station.dat
  summit.dat
```

Fallback executable names are also supported:

| OS | Candidate executables |
| --- | --- |
| Windows | `windows/GAD.exe`, `win/GAD.exe`, `GAD.exe`, `gad.exe` |
| Linux | `linux/GAD`, `linux/gad`, `GAD`, `gad`, `GAD-linux`, `gad-linux` |
| macOS | `macos/GAD`, `macos/gad`, `mac/GAD`, `mac/gad`, `GAD`, `gad`, `GAD-macos`, `gad-macos` |

On Linux and macOS, Swarm attempts to mark the selected executable as runnable before launching it. If macOS blocks the executable, remove quarantine or approve it through macOS security settings outside Swarm.

### GAD Output Time

Successful GAD runs append hypocenter rows to:

```text
GAD/hypocenter.csv
```

The `time` column is written as a full UTC timestamp:

```text
yyyy-MM-dd HH:mm:ss.SSS
```

Swarm derives this value from the linked event base minute plus GAD's `T` seconds value. This avoids malformed partial times such as `21:40.3` or negative-second timestamps such as `2026-08-03 16:26:-10.999`. Existing malformed rows are not rewritten automatically; rerun GAD to append corrected rows.

## Seismicity Statistics

`Tools -> Seismicity Statistics` reads catalog CSV files and summarizes events by classification and note.

The date selector supports years `2000` through `2035`.

For normal catalog rows, statistics use the normal catalog amplitude and duration fields.

For coupled catalog rows, statistics use:

| Statistics field | Coupled catalog source |
| --- | --- |
| Time | `P1` / `originTime` |
| Amplitude | `peakToPeakP2P1` |
| Duration | `durationEndP1Sec` |
| Event type | selected classification |
| Note | `Coupled Events` |

The statistics table includes a `Note` column, so normal and coupled rows remain separate even when they share the same event classification.

## Project Structure

| Path | Purpose |
| --- | --- |
| `src/main/java/gov/usgs/volcanoes/swarm` | Main application source. |
| `src/main/java/gov/usgs/volcanoes/swarm/event` | Event picking, SwarmTagger, catalog tools, Hypo71, GAD, and event utilities. |
| `src/main/java/gov/usgs/volcanoes/swarm/heli` | Helicorder display and interaction. |
| `src/main/java/gov/usgs/volcanoes/swarm/wave` | Waveform views, clipboard, status, and wave interaction. |
| `src/main/java/gov/usgs/volcanoes/swarm/statistics` | Catalog parsing and seismicity statistics. |
| `docs` | User and technical documentation. |
| `layouts` | Example or saved layout resources. |
| `mapdata` | Mapping resources. |
| `GAD` | GAD runtime folder containing input files and platform-specific executables. |

## Development Notes

Recommended verification before committing Java changes:

```powershell
mvn -DskipTests compile
```

Guidelines for working in this fork:

- Keep classification-dependent behavior in shared helpers when possible.
- Keep normal and coupled event catalog flows separate.
- Preserve backward compatibility for existing tagger CSV files.
- Rebuild catalogs after tagger-file format or calculation changes.
- Update this README when changing operator workflows, catalog headers, or statistics mappings.

## Documentation

Additional documentation:

- `docs/Swarm User Guide.docx`
- `docs/swarm_v3.pdf`
- `docs/hypo71manual.pdf`
- `CHANGES.md`

## License and Attribution

Swarm is open source and public domain under CC0 1.0 Universal. See `LICENSE.md` and `DISCLAIMER.md`.

Swarm was originally developed by Dan Cervelli and Peter Cervelli for the USGS Volcano Science Center. Later maintenance and enhancements were made by Tom Parker and Diana Norgaard, with contributions from Loren Antolik, Kevin Frechette, Anthony Lomax, Dave Ketchum, Chirag Patel, Ivan Henson, and others.

DOI: <https://doi.org/10.5066/P93A9MWK>
