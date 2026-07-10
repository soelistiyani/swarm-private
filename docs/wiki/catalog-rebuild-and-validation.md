# Catalog Rebuild and Validation

Catalog tools rebuild daily CSV catalogs from SwarmTagger files.

## Normal Catalog Files

Normal catalogs are written under:

```text
catalog/YYYY/YYYYMMDD/
```

Files:

```text
Swarm_EventCatalog_YYYYMMDD_ALL.csv
station/Swarm_EventCatalog_YYYYMMDD_STATION.csv
```

## Coupled Catalog Files

If coupled tagger files exist for a day, rebuild also writes:

```text
Swarm_CoupledEventCatalog_YYYYMMDD_ALL.csv
station/Swarm_CoupledEventCatalog_YYYYMMDD_STA_CHA_NET_LOC.csv
```

## Validation

Validation checks common problems such as:

- missing classification,
- missing `P`,
- missing `END`,
- multiple picks for one phase,
- `S` before `P`,
- `END` before or equal to `P`,
- very short duration,
- very long duration unless `allowLongDuration` is configured,
- missing amplitude,
- missing frequency unless `skipMdFrequency` is configured.

## When to Rebuild

Rebuild the catalog after:

- adding or editing picks,
- deleting picks or events,
- editing tagger CSV files manually,
- changing classification behavior flags,
- importing or reviewing existing tagger files.
