# Installation and First Run

## Requirements

- Java 8 or newer.
- Maven, if building from source.
- Access to the waveform data sources used by your observatory.

## Build From Source

From the repository root:

```powershell
mvn -DskipTests compile
mvn -DskipTests package
```

## Run Swarm

On Windows:

```powershell
.\swarm.bat
```

With console output:

```powershell
.\swarm_console.bat
```

On Unix-like systems:

```sh
./swarm.sh
```

## First Setup Checklist

1. Confirm waveform channels are available in Swarm.
2. Open a helicorder view for the station/channel you want to pick.
3. Set the SwarmTagger root directory from the tag menu.
4. Confirm `EventClassifications.config` contains the classifications used by your observatory.
5. Pick a test event and rebuild the catalog for that day.
