# Configuration Files

Swarm uses several root-level configuration files. The most important files for tagging and reporting are listed below.

| File | Purpose |
| --- | --- |
| `EventClassifications.config` | Event labels, colors, and optional behavior flags. |
| `TaggerDirectory.config` | Stores the selected SwarmTagger root directory. |
| `SwarmGroups.config` | Channel and group definitions. |
| `SwarmMetadata.config` | Station metadata and multipliers used by statistics tools. |
| `WaveDefaults.config` | Default wave-view settings. |
| `RsamDefaults.config` | RSAM defaults. |
| `Hypo71.config` | Hypo71 settings. |

## Tagger Directory

The tagger directory is the root folder where Swarm writes tagger and catalog files.

Expected structure:

```text
TAGGER_ROOT/
  tagger/YYYY/YYYYMMDD/STATION/
  catalog/YYYY/YYYYMMDD/
```

Use the tag menu to set this directory before picking events.
