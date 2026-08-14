> Migrated from `IMAC-ORG/imac-judge-app`#<!---->94 — originally opened by @<!---->dpgarceau on 2025-10-15T20:50:07Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/94

We need to look at the figures folder structure and see if we can align it with this to have the device select the right image data from the image/audio generation tools I just setup:

These are how the files are created, and could be pushed to the device - so we need to update for the device to know what to look for (short_desck in sequences.dat defines the folder name) and where...

My thought are to simply what is in the figure/en/ folder to three sub folders, default audio, default folders, event folders.
The only reason to separate default folders and event folders is that its easier to clear everything at once in event folders without the risk of losing default data files.  

/figures/en/
      	audio/		// contains all default audio (score numbers, now judging etc.)
      	default/		// contains default image and audio files
		BAK_26S
		SPK_26S
		INK_26S
		INK_26A
		... 		// follows defined folder structure order
	event/
		SPU_8TD		// example Sportsman unknown folder
		UNU_YY6		// example Unlimited unknow folder
		...  // file names generated during creation

Event files could stay in place until system software updates ? or some other clean up command. File sizes are pretty small so wont ever be an issue. 

Folder name format is CCK_### (saved in sequences.dat score_short_desc for lookup)

CC = Class, BA -BAsic, SP -SPortsman, IN - Intermediate, IV -Invitational etc..
K = Known / Unknown
_ = Separator
### is an identifier. 	26S = 2026 Standard Seq, 26A = 2026 Alt Seq, or *** = Unique identifier generated for that sequence.

Examples:
BAK_26S = Basic Known 2026 Std Sequence
ULK_26A = Unlimited Known 2026 ALT Sequence
ADU_TD4 = Advanced Unknown - Event specific


---

@<!---->dpgarceau commented on 2025-11-22T03:08:16Z:

# Proposed Figures/en Folder Structure

Flat structure using folder naming convention instead of deep nesting. This is to support the folder naming structure generated from the AeroJudge sequence conversion processes. 

## Folder Structure

```
figures/en/
├── default/                      # Annual default sequence folders
│   ├── ADK_26S/                  # Advanced Known 2026 Standard
│   ├── ADK_26A/                  # Advanced Known 2026 Alternate
│   ├── ADU_26S/                  # Advanced Unknown 2026 Standard
│   ├── BAS_26S/                  # Basic Known 2026 Standard
│   ├── ...
│   └── FRSTYLE/                  # Freestyle 2026 Standard
├── audio/                        # Shared audio (scores, announcements)
└── event/                        # Custom event uploads (clearable)
    ├── ADK_X7F/                  # Advanced Known, custom round
    └── INU_RR5/                  # Intermediate Unknown, custom round
```

## Folder Naming Convention

### Known/ Unknown Default Sequences

```
{CLASS}{TYPE}_{YEAR}{VARIANT}
```

| Code | Meaning |
|------|---------|
| **Class** | `BA` = Basic, `SP` = Sportsman, `IN` = Intermediate, `AD` = Advanced, `UN` = Inlimitided, `IV` = Invitational |
| **Type** | `K` = Known, `U` = Unknown |
| **Year** | `26` = 2026, `25` = 2025, etc. |
| **Variant** | `S` = Standard, `A` = Alternate |

### Freestyle Sequence ###

```
FRSTYLE
```

### Custom Sequences

```
{CLASS}{TYPE}_{RANDOM}
```

| Code | Meaning |
|------|---------|
| **Class.Type** | Same as above |
| **Random** | 3-character key for specific round/flight |

### Examples

| Folder | Type | Meaning |
|--------|------|---------|
| `ADK_26A` | Default | Advanced Known 2026 Standard |
| `BAS_26S` | Default | Basic Known 2026 Alternate |
| `ADK_X7F` | Custom | Advanced Known, round-specific |
| `SPU_RM3` | Custom | Sportsman Unknown, round-specific |

## Folder Contents

### Sequence Folders (e.g., `default/ADK_26S/`)

Internal filenames unchanged:

```
ADK_26S/
├── 1B.svg, 1C.svg ... 12B.svg, 12C.svg
├── fig1.mp3 ... fig12.mp3
└── call1.mp3 ... call10.mp3
```

### `audio/`

```
audio/
├── 0.mp3 ... 10.mp3              # Whole scores
├── 0_5.mp3 ... 9_5.mp3           # Half-point scores
├── break.mp3
├── not_observed.mp3
└── now_judging_*.mp3, ...
```

### `event/`

Custom uploads with 3-character random key:

```
event/
├── ADK_X7F/
│   ├── 1B.svg
│   ├── fig1.mp3
│   └── ...
└── SPU_RM3/
    └── ...
```

## Comparison

| Aspect | Current | Proposed |
|--------|---------|----------|
| Depth | 4-5 levels (`alt/known/advanced/`) | 2 levels (`sequences/ADK_26S/`) |
| Folders | 27 directories | 3 root + sequence folders |
| Custom events | Not supported | `custom/` folder (clearable) |

## Lookup Priority

1. Get `{short_desc}` from sequences.dat (round and type specific input)
2. Lookup `event/{short-desc}/` (For custom content. Use if matched)
3. If no match use appropriate default `sequences/{class}_{year}{variant}/`


## Migration

- `std/known/advanced/` → `sequences/ADK_26S/`
- `alt/known/advanced/` → `sequences/ADK_26A/`
- `score/` → `audio/`
- `now_judging_*.mp3` → `audio/`


---

@<!---->dpgarceau commented on 2025-11-27T19:57:51Z:

Completed, Nov 2025 - Included in proposed version 2.0
This was what started my rabbit hole venture! 
