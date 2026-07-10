# Long Duration Event Picking

Some event classes can last minutes, hours, or days. Examples include:

- `Tremor Menerus`
- `Lahar`
- `Banjir/Lahar`
- `Tektonik Jauh`

Configure these classes with:

```text
skipMdFrequency,allowLongDuration
```

## Recommended Daily Workflow

For events that continue across day boundaries, pick one daily segment for reporting.

Example:

```text
P at event start or day start
END near 23:59
```

If the event continues the next day, create a new event segment on the next day.

## Calculation Behavior

For classes with `allowLongDuration`:

- `durationSec` remains the full `P -> END` duration,
- validation does not warn when duration is longer than 600 seconds,
- amplitude is calculated only from the first 300 seconds after `P`,
- `skipMdFrequency` can leave `Md`, dominant frequency, and `f1/f2/f3` blank.

This allows long events to be included in reports without forcing Swarm to scan many hours of waveform data for amplitude.

## Picking Other Events During Continuous Tremor

Other events can still be picked inside the time range of a long-duration event. They use their own classification and `eventId`.
