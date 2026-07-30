> Migrated from `IMAC-ORG/imac-judge-app`#<!---->65 — originally opened by @<!---->DevDavidW on 2025-03-17T02:47:31Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/65

Need to find a reliable way to actually update the judge_update.sh script!

Once controller is done, changes to this can be pushed out from there.

But still would be good when you didn't have controller running.


---

@<!---->rolfbartels commented on 2025-03-17T04:48:30Z:

Move content for the judge_updare.sh to another script get the jidge_update.sh to pull the new script and execute.
judge_update.sh can simply be

Check version, pull new script if there is a new version and run it

---

@<!---->dpgarceau commented on 2025-11-28T01:28:37Z:

Completed, Nov 2025 - Included in proposed version 2.0
Added an update button in the menu that calls upon the update script from the server. This allows changes to be made server side as needed without having to update the update script on the device. 
