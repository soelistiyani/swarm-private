# Event Classification Setup

Event classifications are configured in `EventClassifications.config`.

Basic format:

```text
Classification Name,#RRGGBB
```

Example:

```text
LF,#00FFFF
Lahar,#8B4513
Tektonik Jauh,#000080
```

## Behavior Flags

Optional flags can be added after the color.

```text
Classification Name,#RRGGBB,flag1,flag2
```

Supported flags:

| Flag | Behavior |
| --- | --- |
| `skipMdFrequency` | Keep duration and amplitude, but leave `Md`, dominant frequency, and `f1/f2/f3` blank for normal events. |
| `allowLongDuration` | Allow long `P -> END` duration without validation warning and calculate amplitude from only the first 300 seconds after `P`. |

Recommended examples:

```text
Tremor Menerus,#FF00FF,skipMdFrequency,allowLongDuration
Lahar,#8B4513,skipMdFrequency,allowLongDuration
Tektonik Jauh,#000080,skipMdFrequency,allowLongDuration
Banjir/Lahar,#008080,skipMdFrequency,allowLongDuration
```

Flags are explicit. Swarm does not automatically infer translated names or aliases.
