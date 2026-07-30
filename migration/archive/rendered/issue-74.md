> Migrated from `IMAC-ORG/imac-judge-app`#<!---->74 — originally opened by @<!---->dpgarceau on 2025-04-18T17:20:54Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/74

![Image](https://github.com/user-attachments/assets/76fea904-7e86-4426-9d8f-49e6b1f3df2c)

To follow my mapping idea above, the new GPIO pin assignments would be:
GPIO15	Back
GPIO14	+1
GPIO22	+0.5	
GPIO27	Caller
GPIO4	Not Observed
GPIO20	Next
GPIO21	-1
GPIO5	-0.5
GPIO6	Zero
GPIO13	Break

Which should mean the new code is:
# gpio-keys for DPG builds with board rev 2.1 after SN DPG-061
dtoverlay=gpio-key,gpio=15,active_low=1,gpio_pull=up,keycode=5  # back
dtoverlay=gpio-key,gpio=14,active_low=1,gpio_pull=up,keycode=10  # +1.0
dtoverlay=gpio-key,gpio=22,active_low=1,gpio_pull=up,keycode=9   # +0.5
dtoverlay=gpio-key,gpio=27,active_low=1,gpio_pull=up,keycode=11  # caller
dtoverlay=gpio-key,gpio=4,active_low=1,gpio_pull=up,keycode=2  # not_observed
dtoverlay=gpio-key,gpio=20,active_low=1,gpio_pull=up,keycode=7  # next
dtoverlay=gpio-key,gpio=21,active_low=1,gpio_pull=up,keycode=4   # -1.0
dtoverlay=gpio-key,gpio=5,active_low=1,gpio_pull=up,keycode=3  # -0.5
dtoverlay=gpio-key,gpio=6,active_low=1,gpio_pull=up,keycode=6   # zero
dtoverlay=gpio-key,gpio=13,active_low=1,gpio_pull=up,keycode=8  # break

