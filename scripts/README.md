# App services and script compatibility

Device setup and maintenance scripts, along with the assets they use, moved
to [device-scripts](https://github.com/AeroJudge/device-scripts) in October 2026.

App-owned services and their launchers remain here:
- `judge.service` and `judge.sh` — judging application.
- `kiosk.service` and `kiosk.sh` — touchscreen kiosk.

Forwarding scripts preserve embedded URLs used by older systems and are
expected to remain for the foreseeable future:
- `judge_setup.sh` — setup URL used by existing base-image launchers.
- `judge_update.sh` — update URL used by older installed fetchers.

Older installed fetchers retain the original app-repository update URL;
ordinary app updates do not replace them. Existing base images also retain
the original setup URL. Do not remove these forwarders based on elapsed time.
Retirement requires a separate migration of those callers or confirmation
that they are no longer in use.
