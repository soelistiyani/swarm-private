# Coupled Event Picking

Coupled events are picked with a separate menu so they do not enter the normal event catalog by accident.

## Workflow

1. Select the normal event classification first.
2. Open `Coupled Event` from the tag menu.
3. Pick `P1`.
4. Pick `P2`.
5. Optionally pick `P3`.
6. Pick `END`.

Allowed sequences:

```text
P1 -> P2 -> END
P1 -> P2 -> P3 -> END
```

## Rules

- `P2` requires `P1`.
- `P3` requires `P2`.
- `END` requires `P2`.
- If `P3` exists, `END` must be after `P3`.
- `END` marks the end of the whole coupled event.
- Coupled `END` is displayed as `C-END` on the helicorder.
- After picking `P1`, finish coupled `END` before starting normal phase picks for the same event.
- After picking normal `P`, finish normal `END` before starting coupled picks for the same event.
- The note is automatically set to `Coupled Events`.

## Display

Coupled events use the same classification color as normal events, but the helicorder duration block from `P1` to `END` is drawn with diagonal hatching. This keeps classification color meaningful while visually separating coupled events from normal `P -> END` events.

## Measurements

Coupled catalogs store:

- `P2-P1` duration and peak-to-peak amplitude,
- optional `P3-P2` duration and peak-to-peak amplitude,
- `END-P1` total duration,
- `END-P2` duration and peak-to-peak amplitude,
- amplitude ratio,
- dominant frequency and `f1/f2/f3` fields for coupled windows.

For Seismicity Statistics, coupled events use:

- time: `P1`,
- amplitude: peak-to-peak amplitude from `P1 -> P2`,
- duration: `P1 -> END`,
- event type: selected classification,
- note: `Coupled Events`.

## Deleting

`Delete Coupled Event` removes the classification tag and all coupled picks for the event.

## Reviewing Existing Files

`Open existing swarmtagger file` accepts only SwarmTagger input files:

```text
SwarmTagger_*.csv
SwarmTaggerCoupled_*.csv
```

Catalog output files such as `Swarm_EventCatalog_*.csv` are rejected. You can open a normal `SwarmTagger_*.csv` file and then open a `SwarmTaggerCoupled_*.csv` file; both normal and coupled tags remain visible together on the helicorder.
