# Swarm Wiki

This wiki explains how to use the PVMBG-oriented Swarm workflows for event picking, SwarmTagger files, catalogs, coupled events, and seismicity statistics.

## Recommended Reading Order

1. [Installation and First Run](installation-and-first-run.md)
2. [Configuration Files](configuration-files.md)
3. [Event Classification Setup](event-classification-setup.md)
4. [Normal Event Picking](normal-event-picking.md)
5. [Long Duration Event Picking](long-duration-event-picking.md)
6. [Coupled Event Picking](coupled-event-picking.md)
7. [Catalog Rebuild and Validation](catalog-rebuild-and-validation.md)
8. [Seismicity Statistics](seismicity-statistics.md)
9. [File and Folder Naming](file-and-folder-naming.md)
10. [Troubleshooting](troubleshooting.md)

## Main Concepts

SwarmTagger stores picks as CSV files. Each event has an `eventId`, a classification, operator information, optional note text, and phase picks.

Normal events use this workflow:

```text
Classification -> P -> optional S -> END
```

Coupled events use this workflow:

```text
Classification -> Coupled Event P1 -> P2 -> optional P3 -> END
```

Long-duration classes such as continuous tremor, lahar, flood/lahar, and distant tectonic events can be configured so they keep long duration values without producing duration-magnitude or frequency outputs.
