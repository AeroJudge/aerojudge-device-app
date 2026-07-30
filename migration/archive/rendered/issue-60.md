> Migrated from `IMAC-ORG/imac-judge-app`#<!---->60 — originally opened by @<!---->DevDavidW on 2025-03-15T22:46:44Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/60

This will bring scores back to the box that may be on a different line.
This may also be setup to bring back corrections from Score


---

@<!---->rolfbartels commented on 2025-03-16T04:16:13Z:

This will require a re-write to score, would it not.make sense to start looking into the judge controller,.and having aerojudge sync there ?
Judge controller can track the flown, rounds

For me it makes more sense to track flown rounds rather than the actual scores.

---

@<!---->DevDavidW commented on 2025-03-17T02:37:54Z:

I don't think it would need a rewrite in score. We can just add methods needed to convert the score flight data back to the judge format (if that doesn't already exist) in the XMLio class and then just add a service to return back the scores.

We talked through the idea of not having the boxes keep track of rounds. But how could that be reconciled in the controller? We can't rely on date/time stamp in the boxes (unless we require a score sync before you can start judging after a box restart).

Round numbers work well, but if you were to start a round on another line, the box needs to know the last round flown for that pilot. It would be best to get that back from score (but requres we sync to score from the first line). Perhaps we don't need the scores themselves, but just return the last round for the pilot so we can set "activeRound" as lastflown+1.

---

@<!---->rolfbartels commented on 2025-03-17T04:45:23Z:

The issue you are having with starting a round at another line is because you are making AeroJudge devices  dedicated to lines.
I would prefer to see them being agnostic to lines.

So yes we would need to track pilots and flown rounds.

This was going to be part of judge controller,  where devices would get all pilots and next round for the pilot. 
whats the purpose of a line ?
If we simply track the pilot and his next round then lines become un-needed and Aero Judge s don't need to be dedicated to a line.

I'm not opposed to a 2 way sync, just think if we pull all scores back to the device then we have to manage versioning, as we don't want to over ride the original scores with potential changed scores as this opens the system to abuse.

If we just pull and use pilot flown rounds
This will also.solve the issue where all line 2 flights start at R21, 
Also I think if remember if you go above 10 rounds then R21 becomes R211, as we use R31 for Lines 3

We wanted to change the data set for AeroJudge to json, to allow more details and better structure, if we add to the data set 
Pilot, Class, Round, Sequence, Scores, Judge and there where a few others to the data set we could have better control, there ehave been several discussions about this before

Maybe we should brain storm this in slack 
We need to make a call on if we are ever going to move forward with a JudgeController, or just stick with score and manage it all there


---

@<!---->e-harvey commented on 2025-08-22T17:02:05Z:

This issue sounds interesting. I’d like to understand more. How do I join the slack channel?
