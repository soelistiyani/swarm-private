# Normal Event Picking

Normal event picking is used for ordinary classified events with one main `P -> END` duration window.

## Workflow

1. Right-click the helicorder view.
2. Choose `Event Classification` and select the event type.
3. Pick `P`.
4. Optionally pick `S`.
5. Pick `END`.

Required sequence:

```text
Classification -> P -> optional S -> END
```

Normal and coupled picks are mutually exclusive while an event is in progress. After picking normal `P`, finish normal `END` before starting a `Coupled Event` pick for the same event.

## Calculated Fields

When `END` is picked, Swarm stores:

- full duration from `P` to `END`,
- max absolute amplitude,
- max amplitude time,
- peak-to-peak amplitude,
- duration magnitude `Md`, unless skipped by classification flag,
- dominant frequency and `f1/f2/f3`, unless skipped by classification flag.

The helicorder shows a translucent duration block from `P` to `END` using the selected classification color.

## Re-picking

Picking the same phase again replaces the old phase pick for the same event.

## Deleting

Use the delete pick menu to remove individual phase picks. Deleting `P` also removes dependent `S` and `END` picks. Use delete event to remove the classification tag and all picks for the event.
