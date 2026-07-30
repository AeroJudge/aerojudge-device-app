> Migrated from `IMAC-ORG/imac-judge-app`#<!---->3 — originally opened by @<!---->DevDavidW on 2023-06-19T03:50:38Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/pull/3

_Original was a pull request (MERGED)._




---

@<!---->rolfbartels commented on 2023-06-19T09:29:06Z:

As in Score 4.52 if Comp_ID is blank for more than 1 pilot then we can't save data files and we get a pop-up message 
That Comp number conflits with another pilot: Please fix or unpredictable results will occur.
If we don't enter any comp_id for any pilots the Score is ok, as soon as to add a comp_id and then delete it we get this msg.

---

@<!---->DevDavidW commented on 2023-06-20T02:28:07Z:

I've added the same check for secondary_id. I realize in Score that secondary_id is marked required in the preferences but it can be turned off. So yes makes sense to use the same check there also. I'm assuming that the element itself will always exist within the pilot node. It seems that for this version it will have secondary_id or comp_id elements even if there is no value provided.
