> Migrated from `IMAC-ORG/imac-judge-app`#<!---->66 — originally opened by @<!---->rolfbartels on 2025-03-18T15:45:07Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/66

Selecting unknow for a class that does not have a unknow sequence throws an error.

We need to disable/greyout the unknow if a sequence for this class does not exist, this will prevent the judge from selecting it.


---

@<!---->dpgarceau commented on 2025-04-08T02:52:23Z:

I've run into that while testing - but have generic unknowns in all of my contest files so its never an issue. instead of making it dynamic, wouldnt it be fixed by default when we have open / close round functions? 


---

@<!---->dpgarceau commented on 2025-11-27T19:46:33Z:

Completed, Nov 2025 - Included in proposed version 2.0
this version only allows you to select sequences that have an entry in sequences.dat - but even if that was somehow bypassed, then the noseq.html screen provides a warning and escape path instead of being a dead end. 
