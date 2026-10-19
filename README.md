# AeroJudge Device App

## Project Direction

AeroJudge Device App is the device-side application for the official
AeroJudge Scoring System.

Current development focuses on the AeroJudge Device product from serial number DPG-100 and above containing PCB3.6x and later.

1. Maintain the AeroJudge Device App / AeroJudge Device product identity,
   package namespace, release versioning, and update process.
2. Keep setup scripts, device configuration, and hardware support aligned with
   current production AeroJudge Device hardware.

Changes will be made through focused branches and reviewed before merging to `main`. The intent is to avoid broad live edits on `main` and keep identity, release, setup, and hardware changes separated enough to review safely.

## Repository History

AeroJudge Device App continues the work that originated as SA-Pi-Score and the IMAC Judge App. The
final AeroJudge repository preserves the project commit and tag history while
moving the active product identity, package namespace, release process, and
setup path to AeroJudge Device App.

Historical migration details are recorded in [`migration/README.md`](migration/README.md).
Those files document the one-time repository assembly and preserved metadata;
they are not part of the normal development workflow.

## Device Setup

Current production AeroJudge Device setup instructions are maintained in
[device-scripts/SETUP.md](https://github.com/AeroJudge/device-scripts/blob/main/SETUP.md). Setup implementation is maintained in
[device-scripts](https://github.com/AeroJudge/device-scripts). Existing
`scripts/judge_setup.sh` URLs remain compatibility entry points for AeroJudge Device serial `DPG-110` and above with
PCB revision `3.61+`.


## Developer Environment

### Requirements

1. VSCode
2. Docker Desktop
3. Score >= v4.71 with services enabled and started
4. Java 17 or later (Java 21 works fine)

### Run AeroJudge Device App Locally

1. Create or edit `/var/opt/judge/settings.json` with the correct Score host
   and port.

```sh
sudo vi /var/opt/judge/settings.json

eg.
{
  "judge_id":1,
  "line_number":1,
  "score_host":"192.168.8.100",
  "score_http_port":80,
  "language":"en",
  "seasonYear":26
}
```

2. Run the app:

```sh
cd judge
./mvnw spring-boot:run
```

3. Open <http://localhost:8080> in a local browser.

### Build AeroJudge Device App

To build the application package:

```sh
cd judge
./mvnw clean package
```

Local Maven builds create a versioned JAR in `judge/target/`. Device installs
and release assets must use the runtime filename `judge.jar`.

For a manual device copy, rename the built JAR on the device:

```sh
cp judge/target/judge-26.1.0.jar /var/opt/judge/bin/judge.jar
```

GitHub release automation publishes stable asset names: `judge.jar`,
`figures.zip`, and `volume_service.zip`.

### Run AeroJudge Device App In Docker

1. Build the application package:

   ```sh
   cd judge
   ./mvnw clean package
   cd ..
   ```

2. Build and run the Docker image:

   ```sh
   docker build -t aerojudge-device-app:latest .
   docker run -p 8080:8080 aerojudge-device-app:latest
   ```

   The image includes `dockerbuild/settings.json` as its default
   `/var/opt/judge/settings.json`. Keep that file aligned with the defaults in
   `SettingDTO`, then edit it before building or mount a local settings file
   when running:

   ```sh
   docker run -p 8080:8080 -v /path/to/settings.json:/var/opt/judge/settings.json aerojudge-device-app:latest
   ```

3. Open <http://localhost:8080> in a local browser.
