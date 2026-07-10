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
- The note is automatically set to `Coupled Events`.

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
