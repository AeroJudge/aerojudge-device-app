> Migrated from `IMAC-ORG/imac-judge-app`#<!---->70 — originally opened by @<!---->DevDavidW on 2025-04-10T03:16:06Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/70

Need a way to have sets of images, descriptions, and audio files to be selected based on the data in the sequences.dat file which indicate the round numbers.

Possible solution: Update Score Sequences tab to allow selection of a "Set" to include: Standard, Alt, Unkown Generic, A, B, C, D, E
This will require an update to the sequences.dat file

What about storing this data such that it aligns with the Score rounds
Possible scenario for NATS
Day 1
- Rounds 1-2 Knowns A
- Round 3 Unknown A
Day 2
- Rounds 4-5 Knowns B
- Round 6 Unknown B
Day 3
- Rounds 7-8 Knowns A
- Round 9 Unknown C
Day 4
- Rounds 10-11 Knowns B
- Round 12 Unknown D


---

@<!---->rolfbartels commented on 2025-04-10T04:34:47Z:

I'd like to understand what the issue is that requires this ? As it is, based on if you select known,known, std, alt  this sorts out the correct fields to be used, 
What happens when mid comp the round orders are changed ? How do we cater for that, maybe we can have a discussion on slock

---

@<!---->DevDavidW commented on 2025-04-11T01:54:11Z:

This is just for the big contests which will be running multiple knowns and/or unknowns. David and I talked through this yesterday a good bit and this is just the initial thoughts on how to accomplish it.

---

@<!---->dpgarceau commented on 2025-11-27T19:50:53Z:

Completed, Nov 2025 - Included in proposed version 2.0
The device uses/ displays the sequence content as directed by the sequences.dat round numbering. Validation is handled upon load to identify issues and fallbacks exist for conflicts. 
