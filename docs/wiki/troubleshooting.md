# Troubleshooting

## Catalog Does Not Include My Event

Check:

- the event has a classification tag,
- required phase picks exist,
- the tagger file is under the expected day folder,
- the catalog was rebuilt after picking,
- coupled events are checked in the coupled catalog, not the normal catalog.

## Md or Frequency Is Blank

Check `EventClassifications.config`. If the classification has `skipMdFrequency`, blank `Md`, dominant frequency, and `f1/f2/f3` are expected for normal event output.

## Long Event Shows No Long-Duration Warning

If the classification has `allowLongDuration`, validation suppresses the `Duration too long (>600 s)` warning. This is expected for long-duration classes such as continuous tremor, lahar, flood/lahar, and distant tectonic events.

## Coupled Event Filename Looks Too Long

Expected coupled filename format:

```text
SwarmTaggerCoupled_YYYYMMDD_STA_CHA_NET_LOC_OPERATOR.csv
```

If extra `UNKNOWN` tokens appear, confirm the channel string and rebuild after using the current code.

## Seismicity Statistics Counts Look Duplicated

Normal and coupled events can share the same classification. Use the `Note` column to distinguish normal rows from `Coupled Events` rows.

## Amplitude Is Based Only on First 300 Seconds

This is expected for classifications with `allowLongDuration`. Full duration is preserved, but amplitude scanning is capped to avoid processing very long windows.
