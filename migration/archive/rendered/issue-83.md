> Migrated from `IMAC-ORG/imac-judge-app`#<!---->83 — originally opened by @<!---->dpgarceau on 2025-06-29T16:04:29Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/83

Here is the situtation that I just remotely fixed for someone running a contest.

Contest is setup for two rounds of knowns, 1 round of unknowns.

late in the first day - they decide to only fly one known round - So round 3 sequence 1 was flown but they exited out of sequence 2.

The next morning the flew unknowns which was saved in the device as Round 3 sequence 2. 
All the data is OK at this point and uploaded correctly into score...

BUT the NextRound and Next Sequence stayed as round 3 sequence 2 - even though that had been used for the unknown round. So when they went to fly another known it would not save the scores (because there were already r3s2 scores saved).

To fix I manually changed each pilot (on both devices) to show nextround 4 and next sequence 1.



---

@<!---->rolfbartels commented on 2025-07-30T16:27:29Z:

A possible fix for this would be to at the save/review screen have a button "Continue to Seq 2" and "Save exit to home"
first one does as it does now.
2nd one save the data and enters blank scores for Seq2

---

@<!---->DevDavidW commented on 2025-08-22T03:26:19Z:

Perhaps the other possibility is if the type of the last scored round is different that the new selection (last was KNOWN and now scoring UNKNOWN) that we automatically assume it is the next whole round number (ie it skips the second sequence altogether). That assumes thats the only scenario for this. If the pilot were to fly another known the next day, would it be a single sequence to "make-up" the difference or should it also be a whole new round number?

---

@<!---->rolfbartels commented on 2025-08-22T17:39:14Z:

What happens, if you fly knows today 1seq stop and fly tomorrow 2seq. Known

My first option is not a great one, if a judge miss presses he'll close the round of the pilot.
So we could give the judge an option to close seq2 at the pilot screen, or we build into the Judge Controller the option to close rounds.

What does Score do if you have 1 round with only 1 seq, but the rest are 2seq

---

@<!---->dpgarceau commented on 2025-11-27T19:54:24Z:

Completed, Nov 2025 - Included in proposed version 2.0
The device now recognizes incomplete rounds (when only sequence 1 is scored in a two sequence round) and prompts the judge asking if the pilot should be flying the second missing sequence or start a new round. 
This will be very helpful when a pilot has to get out of sequence for an issue and then catch up later - and fixes the "we only ran single sequences at the end of the day" situation. 
