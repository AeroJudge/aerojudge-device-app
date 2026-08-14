> Migrated from `IMAC-ORG/imac-judge-app`#<!---->68 — originally opened by @<!---->DevDavidW on 2025-04-10T01:54:11Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/68

Show signal strength icon and possibly IP address in upper left-hand corner of pilot list screen

If no signal, show greyed icon
Otherwise use 4-bar icon with each bar representing 25% signal strength

First iteration we can just do the icon and IP address
Later the idea would be for this to be dynamic which could then also include battery meter info


---

@<!---->rolfbartels commented on 2025-04-10T04:29:22Z:

If we are moving to a central controller and discovery mesh, would we still need this ? 

---

@<!---->rolfbartels commented on 2025-04-10T04:44:14Z:

Also,  what's the use case ?
IP and signal strength is only needed when setting up or uploading, I would not want to see these functions on the judges screens, maybe we have a systems screen where we can add these rand anything else that comes along, we are going to end up cluttering the displays and detract from the objective

---

@<!---->DevDavidW commented on 2025-04-11T01:51:46Z:

We had two boxes this past weekend that we brought over to the Score computer, but each time we tried to sync to Score, nothing was displayed. So we need to have some status details when doing the sync. In this case, it wasn't connected to WiFi any longer. I guess once a box leaves the WiFi range, it won't auto reconnect when it comes back within range. We ended up having to reboot each device and then the sync worked. But if we "knew" on the device that it wasn't connected it would have avoided confusion. Dan also eventually wants to have the battery meter displayed. We could put these on some other screen. Maybe we have a way to exit the "judging" screens and go to like a main menu type of page?

All of these are "quick" fixes for now. I know eventually these will get superseded with the controller concept - but we could sure use some enhancements for now and this was one of those.

---

@<!---->rolfbartels commented on 2025-04-11T05:24:07Z:

So I think adding the WiFi and battery symbol permanently tomonencorner of the screen like after Judge and Line should be ok, we can abbreviate Judge 1 Line 1 to J1:L1 if space is a problem 
