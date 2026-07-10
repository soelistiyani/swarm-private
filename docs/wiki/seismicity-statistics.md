# Seismicity Statistics

`Tools -> Seismicity Statistics` summarizes catalog rows by classification and note.

## Normal Events

Normal event statistics use the standard catalog fields:

- event time,
- station,
- channel,
- classification,
- amplitude,
- duration.

## Coupled Events

Coupled event statistics use the coupled catalog mapping:

| Statistics field | Coupled catalog source |
| --- | --- |
| Time | `P1` / `originTime` |
| Amplitude | `peakToPeakP2P1` |
| Duration | `durationEndP1Sec` |
| Event type | selected classification |
| Note | `Coupled Events` |

The `Note` column separates normal and coupled rows even when they have the same classification.

## Amplitude Unit Conversion

If metadata multipliers are available in `SwarmMetadata.config`, the statistics tool can also display amplitude in scaled units such as micrometers per second.
