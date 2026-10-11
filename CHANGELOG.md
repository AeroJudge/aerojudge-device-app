# Changelog

This changelog begins with the AeroJudge Device App at v26.1.0.

Earlier release notes are preserved in `CHANGELOG_legacy.md`. Repository
assembly and preserved-history migration details are documented in
`migration/README.md`.

## Unreleased

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
