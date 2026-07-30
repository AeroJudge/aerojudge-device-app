> Migrated from `IMAC-ORG/imac-judge-app`#<!---->89 — originally opened by @<!---->dpgarceau on 2025-08-16T16:24:26Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/89

When the IMAC ID field in the pilot setup contains numbers or characters, it causes Aerojudge to freeze with the white label error.

It would be better to have it limit to numeric values only. 

We will have to collectively decide what to do for new pilots that dont have an IMAC number as using 99998 and 99999 which is what i just had to do arent exactly good options.

_____________________
From my Slack post:
Is it possible to have SCORE restrict the IMAC numbers on the pilots setup page to only allow digits? (i.e. to NOT allow letters or characters in that field?)
I just spent two hours fixing multiple setup errors for a contest that was starting this morning.
Amongst other minor things, the big one was that they had someone show up as a new pilot and entered "TBD" in his IMAC number field which of course immediately locked out the boxes with the white label error.

Thanks
David G.



---

@<!---->rolfbartels commented on 2025-08-16T16:36:44Z:

Why restrict it, let's fix the AeroJudge to support alpha numeric, 

---

@<!---->sputnik-dancarroll commented on 2025-08-16T17:30:11Z:

That's another option I guess Rolf.

---

@<!---->dpgarceau commented on 2025-08-16T17:31:34Z:

thats an option too, and is probably a better option for future foolproofing... either way we need to avoid the white screen issue that happened today!
