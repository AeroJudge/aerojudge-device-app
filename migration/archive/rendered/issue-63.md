> Migrated from `IMAC-ORG/imac-judge-app`#<!---->63 — originally opened by @<!---->DevDavidW on 2025-03-15T22:51:00Z
> Original: https://github.com/IMAC-ORG/imac-judge-app/issues/63

Currently if you hold a button, it advances multiple times in quick succession. It should force the person to release the button and press again to advance an additional value.


---

@<!---->rolfbartels commented on 2025-03-16T04:27:47Z:

You can disable the keyboard repeat delay in Raspbian by adjusting the keyboard settings using xset. Follow these steps:

Method 1: Using xset

1. Open a terminal.


2. Run the following command to disable key repeat entirely:

xset r off


3. If you only want to adjust the repeat rate, you can try:

xset r rate 660 25

The first number (660) is the delay in milliseconds before repeating starts.

The second number (25) is the repeat rate (keys per second).



4. To make the change permanent, add the command to your ~/.xsessionrc or ~/.bashrc:

echo "xset r off" >> ~/.xsession

---

@<!---->DevDavidW commented on 2025-03-17T02:45:24Z:

Was able to do this by adding the two commands to ~/.bashrc:
export DISPLAY=:0
xset r off

This can be added to the judge_update.sh script for implementation during next upgrades.
