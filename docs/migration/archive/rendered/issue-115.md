> Migrated from `IMAC-ORG/imac-judge-app`#<!---->115 — originally opened by @<!---->dpgarceau on 2026-03-15T13:42:51Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/115

I've been asked to add live results (pilot by pilot) for NATS and TAS!

Pressing sync and then uploading all the files seems like a nightmare. So, here is what I'm thinking: 

Devices:
An admin button (or hidden feature) to turn this on for these special events - that will blindly push single pilot / single round scores in the background upon "save" in the device. It ignores errors, so if no internet or failed connection thats ok. 
Any missed data can be synced with a normal manual push scores on demand or at the conclusion of the round with a clean up push. 

Score:
If not a relatively easy update, way need a macro running (say every 30-60 seconds) to import, upload (not overwrite since each one is just a single pilot score), and then publish results. (automate the manual steps of getting results data). Need an easy way to start / stop this in case things have to be fixed.

Web:
Use winscp auto sync to keep the online aero-judge results folder updated with the latest results. 

