> Migrated from `IMAC-ORG/imac-judge-app`#<!---->64 — originally opened by @<!---->DevDavidW on 2025-03-15T22:52:40Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/64

When syncing to Score, make the message larger and stay on the screen a little longer to allow it to be seen better.
Once the sync starts, display status message (Contacting score, sending data, sync complete).

If there is a failure, make it appear different (such as red text, larger font, different location, etc.) so it is more obvious.


---

@<!---->dpgarceau commented on 2025-04-08T02:54:03Z:

I wouldnt mind these needing to be clicked to be cleared - or even have a status so that it says "thinking" as soon as teh button is clicked then changes to an error message (like no wifi connection) or complete once done. 

---

@<!---->rolfbartels commented on 2025-04-08T03:56:33Z:

Just a thought, if we move controls and functions to score or JisgeController, then we should remove the upload from AeroJudge, as this would be initiated from a central location, making this a Mon issue on the units
