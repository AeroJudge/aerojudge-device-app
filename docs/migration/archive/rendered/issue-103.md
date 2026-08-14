> Migrated from `IMAC-ORG/imac-judge-app`#<!---->103 — originally opened by @<!---->dpgarceau on 2025-11-21T20:08:38Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/103

We need to work freestyle scoring back into this project. 

We can have freestyle operate using the same system architecture/ design as a known/ unknown round - in that it pulls figures and audio to be scored. This is logical and works with the existing flow. 

What might get tricky (and we have to test) is how freestyle round numbers are logged in the AeroJudge device and then transmitted to score. 

From score **outputs** (report_pilot_9158.xml) I can see that round numbers for known/ unknown and that freestyle round numbers are separate. 
below you can see KNOWN round 1-3, then unknown round 4
plus freestyle round 1-2 

```
...
<frequency>SS</frequency>
<ama_number>9158</ama_number>
<imac_number>9158</imac_number>
</pilot>
<section type="KNOWN" start_round="1" end_round="3" judge_sheet="1">
...
</section>
<section type="KNOWN" start_round="1" end_round="3" judge_sheet="2">
...
</section>
<section type="UNKNOWN" start_round="4" end_round="4" judge_sheet="1">
...
</section>
<section type="UNKNOWN" start_round="4" end_round="4" judge_sheet="2">
...
</section>
<section type="FREESTYLE" start_round="1" end_round="2" judge_sheet="1">
...
</section>
```

How would this work in aerojudge??? Would score recognize the input with type+roundnumber?


---

@<!---->dpgarceau commented on 2025-11-27T19:58:23Z:

Completed, Nov 2025 - Included in proposed version 2.0
