# File and Folder Naming

## Tagger Folder Structure

SwarmTagger files are written under:

```text
TAGGER_ROOT/tagger/YYYY/YYYYMMDD/STATION/
```

## Normal Tagger Files

```text
SwarmTagger_YYYYMMDD_STATION_OPERATOR.csv
```

Example:

```text
SwarmTagger_20260710_PPKB_ASUS.csv
```

## Coupled Tagger Files

```text
SwarmTaggerCoupled_YYYYMMDD_STA_CHA_NET_LOC_OPERATOR.csv
```

Example:

```text
SwarmTaggerCoupled_20260710_PPKB_HHZ_VG_00_ASUS.csv
```

## Normal Catalog Files

```text
catalog/YYYY/YYYYMMDD/Swarm_EventCatalog_YYYYMMDD_ALL.csv
catalog/YYYY/YYYYMMDD/station/Swarm_EventCatalog_YYYYMMDD_STATION.csv
```

## Coupled Catalog Files

```text
catalog/YYYY/YYYYMMDD/Swarm_CoupledEventCatalog_YYYYMMDD_ALL.csv
catalog/YYYY/YYYYMMDD/station/Swarm_CoupledEventCatalog_YYYYMMDD_STA_CHA_NET_LOC.csv
```

## Channel Name Normalization

For coupled files, Swarm keeps the first meaningful `STA_CHA_NET_LOC` tokens and drops trailing `UNKNOWN` tokens from channel-derived names.
