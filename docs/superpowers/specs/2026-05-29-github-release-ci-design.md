# GitHub Release CI Automation

## Goal

Add a manual GitHub Actions release workflow that builds a signed release APK and publishes it to GitHub Releases without requiring the operator to enter a version number.

## Problem

The project currently has Gradle release builds and date-based app versioning, but no GitHub Actions workflow. Release signing also should not depend on a tracked keystore file in the repository.

The desired release flow is:

1. A maintainer manually runs a GitHub Actions workflow.
2. The workflow builds the release APK.
3. The APK is signed using GitHub Secrets.
4. The workflow creates or updates the matching GitHub Release.
5. Re-running the workflow on the same day replaces the existing APK asset and moves the matching tag to the current commit.

## Approach

Create `.github/workflows/release.yml` using `workflow_dispatch` with no inputs. The workflow will infer the version from the Gradle-generated APK filename, then publish that APK to a GitHub Release named after the derived tag.

The project will also stop tracking local keystore files. Release signing material will be stored in GitHub Secrets, decoded only on the ephemeral GitHub Actions runner, and passed to Gradle during the release build.

## Trigger and Versioning

The release workflow is triggered only by manual `workflow_dispatch`.

No version input is required. Gradle already produces release APKs named like:

```text
jbus_release_v<versionName>.apk
```

After `assembleRelease`, the workflow finds the single release APK under:

```text
app/build/outputs/apk/release/
```

It parses `<versionName>` from the APK filename and derives:

```text
tag = v<versionName>
release name = v<versionName>
```

With the current Gradle rules, this produces tags like `v1.20260529`.

## Re-Release Behavior

If the same version is released again, the workflow intentionally treats the latest run as authoritative.

The workflow will:

1. Move the local tag `v<versionName>` to the current workflow commit.
2. Force-push that tag to GitHub.
3. Create the GitHub Release if it does not exist.
4. Reuse the existing GitHub Release if it already exists.
5. Delete any existing APK asset with the same filename.
6. Upload the newly built signed APK.

This keeps the tag, Release, and APK aligned with the commit used by the latest successful release run.

## Signing

The workflow signs release APKs using GitHub Secrets. Required repository secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

The workflow will decode `ANDROID_KEYSTORE_BASE64` to a temporary file such as:

```text
$RUNNER_TEMP/release-keystore.jks
```

Gradle will receive the keystore path and credentials through project properties or Android signing injection properties during `assembleRelease`.

Gradle release signing should be configured so CI release builds are signed when these values are present. Local builds should not require the CI secrets unless the developer explicitly provides equivalent local properties.

## Keystore Management

The release keystore should not be tracked in git.

Implementation should:

1. Remove the currently tracked `jbus.jks` from the git index while keeping the local file available if needed.
2. Add keystore patterns to `.gitignore`, including:

```gitignore
*.jks
*.keystore
```

Recommended local storage is outside the repository, for example:

```text
D:\Secrets\JBusDriver\release.jks
```

The keystore must be backed up securely. Losing it can prevent smooth upgrades for users who installed APKs signed with that key.

If the tracked keystore has already been pushed to GitHub, it should be treated as exposed. If the app has not yet been distributed with that key, the safest path is to generate a new release keystore and upload only the new key to GitHub Secrets. If users already have APKs signed with the existing key, replacing the key may force users to uninstall before installing future builds.

## GitHub Secrets Setup

To create `ANDROID_KEYSTORE_BASE64` from PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\Secrets\JBusDriver\release.jks"))
```

Then add repository secrets in GitHub:

```text
Settings -> Secrets and variables -> Actions -> New repository secret
```

Add:

```text
ANDROID_KEYSTORE_BASE64      Full base64 output from the PowerShell command
ANDROID_KEYSTORE_PASSWORD    Keystore password
ANDROID_KEY_ALIAS            Release key alias
ANDROID_KEY_PASSWORD         Key password
```

## Operator Workflow

To publish a release:

1. Open the GitHub repository.
2. Go to `Actions`.
3. Select the release workflow.
4. Click `Run workflow`.
5. Select the branch to publish, usually `main`.
6. Run the workflow without entering a version.
7. After success, open `Releases`.
8. Download the APK from the Release named `v1.<yyyyMMdd>`.

To republish the same day's release, run the same workflow again from the desired commit or branch. The workflow will move the same tag and replace the same APK asset.

## Permissions

The workflow needs:

```yaml
permissions:
  contents: write
```

This allows the GitHub Actions token to push tags, create or update GitHub Releases, and upload Release assets.

## Failure Handling

The workflow should fail clearly when:

1. Any required signing secret is missing.
2. The release APK cannot be found.
3. More than one release APK matches the expected pattern.
4. The version cannot be parsed from the APK filename.
5. Tag push fails.
6. Release creation or asset upload fails.

The workflow should not silently publish an unsigned APK.

## Non-Goals

This design does not change `JAVBUS_AUTH_COOKIE` handling. It can continue using the existing Gradle property or environment-variable behavior. Moving it into code or changing its storage can be handled separately if needed.

This design does not publish to Google Play or any other app store.

This design does not require immutable release tags. Same-version re-releases intentionally move the tag to the latest published commit.

## Validation

Local validation:

1. Confirm Gradle release signing configuration still allows local development builds.
2. Confirm CI signing properties match the workflow parameters.
3. Confirm `jbus.jks` is removed from git tracking and keystore patterns are ignored.

GitHub validation:

1. Add all required GitHub Secrets.
2. Run the release workflow manually.
3. Confirm the workflow creates or updates `v1.<yyyyMMdd>`.
4. Confirm the Release contains the signed APK.
5. Run the workflow again on the same day.
6. Confirm the tag points to the latest run commit and the APK asset is replaced.
