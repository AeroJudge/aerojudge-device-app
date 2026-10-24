# Changelog

This changelog begins with the AeroJudge Device App at v26.1.0.

Earlier release notes are preserved in `CHANGELOG_legacy.md`. Repository
assembly and preserved-history migration details are documented in
`migration/README.md`.

## Unreleased

- Redesigned the score summary for the 800x480 judge display with larger rows,
  one-decimal scores, colour identifiers and scrolling for longer sequences.
  Device scoring, navigation and caller keys are blocked while it is open;
  flag display and jump-to-figure support are prepared for project 06 but unused.

- Removed duplicate figures and volume package sources, the unused figures
  assembly descriptor, and their app release triggers; package repositories
  now own those sources and releases.
- Added installed `figuresVersion` and `volumeVersion` to `/api/getinfo`;
  unavailable or invalid package metadata reports an unknown version as null.
- App releases now publish only `judge.jar`. The updater and fresh setup obtain
  `figures.zip` from the `device-figures-pkg` release and `volume_service.zip`
  from the `device-volume-svc` release; installed paths and update behavior are
  unchanged.
- Renamed `AEROJUDGE_RELEASE_API_URL` to `AEROJUDGE_APP_RELEASE_API_URL` and added
  `AEROJUDGE_FIGURES_RELEASE_API_URL` and `AEROJUDGE_VOLUME_RELEASE_API_URL`.
- Moved setup/update/recovery implementations, stored boot profiles and wallpaper
  to `device-scripts`, retaining the existing setup and updater URLs as forwarding entry points and
  preserving app-owned runtime files and existing release assets.
- Added `AEROJUDGE_SCRIPTS_RAW_BASE_URL` for script/config/wallpaper source overrides;
  existing app-source and release API overrides retain their responsibilities.
- Corrected the legacy setup command and remove the modern device-local setup
  launcher/procedure only after successful validation, retaining setup logs.

- Added `scripts/device_identity.sh` to read and write the board identity record (build mark, board revision, serial) in the RV-3028 user EEPROM, with standalone instructions in `scripts/README.md`.
- Added `deviceSerialNumber` and `boardRevision` to `/api/getinfo`, read once at startup from the board EEPROM; null when no valid record is present.
- Fixed where the score summary page showed a raw `-3` instead of `Deadline` for deadline penalties.
- Use `apt-get` for fresh-device package installation to avoid interactive CLI warnings in setup logs.
- Exclude pilots marked `<dropped>true</dropped>` from active-pilot lists while preserving their saved scores and uploads.
- Prepared AeroJudge Device App identity, package namespace, Maven coordinates,
  setup documentation, and release automation for the final AeroJudge
  repository.
- Fixed the local Docker image build path so the container runs the packaged
  app as `judge.jar`, matching device runtime behavior.
- Fixed release workflow handoff so non-release pushes do not create failed
  release runs.
