# GitHub Release CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a no-input manual GitHub Actions release workflow that builds a signed Android release APK and publishes it to GitHub Releases.

**Architecture:** Gradle will sign release builds only when release signing project properties are present. GitHub Actions will restore the keystore from repository secrets, build `assembleRelease`, parse the Gradle-generated APK filename for the version, move the matching tag to the workflow commit, and upload the APK to the matching GitHub Release. Local keystore files will be ignored and the currently tracked `jbus.jks` will be removed from git tracking.

**Tech Stack:** Android Gradle Plugin 9.2.0, Kotlin DSL, GitHub Actions, GitHub CLI, PowerShell for local setup commands.

---

## File Map

- Modify: `app/build.gradle.kts`
  - Add release signing property providers.
  - Create the `release` signing config only when all signing inputs exist.
  - Attach that signing config to the `release` build type.
- Modify: `.gitignore`
  - Ignore local Android keystore files.
- Remove from git index: `jbus.jks`
  - Keep the local file on disk if present, but stop tracking it.
- Create: `.github/workflows/release.yml`
  - Define manual release workflow, signing-secret validation, APK build, version extraction, tag movement, Release creation/update, and asset replacement.

---

### Task 1: Gradle Release Signing Properties

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Run a baseline Gradle configuration check**

Run:

```powershell
.\gradlew.bat :app:tasks --all
```

Expected: Gradle configures successfully and prints the task list.

- [ ] **Step 2: Add release signing property providers**

In `app/build.gradle.kts`, after the existing `javbusAuthCookie` value is defined, add:

```kotlin
val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE")
    .map { it.unquotePropertyValue() }
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD")
    .map { it.unquotePropertyValue() }
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS")
    .map { it.unquotePropertyValue() }
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD")
    .map { it.unquotePropertyValue() }

val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.orNull.isNullOrBlank() }
```

- [ ] **Step 3: Create release signing config only when inputs exist**

Inside the `android { ... }` block in `app/build.gradle.kts`, before `defaultConfig { ... }`, add:

```kotlin
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }
```

- [ ] **Step 4: Attach the signing config to release builds**

Inside `buildTypes { release { ... } }`, add this block before `isMinifyEnabled = true`:

```kotlin
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
```

The `release` build type should start like this after the edit:

```kotlin
        release {
            applicationIdSuffix = ".release"
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
```

- [ ] **Step 5: Verify Gradle still configures without signing properties**

Run:

```powershell
.\gradlew.bat :app:tasks --all
```

Expected: `BUILD SUCCESSFUL`. This confirms local development does not require CI signing secrets.

- [ ] **Step 6: Commit Gradle signing support**

Run:

```powershell
git add -- app/build.gradle.kts
git commit -m "build: support release signing from properties"
```

Expected: A commit is created containing only `app/build.gradle.kts`.

---

### Task 2: Stop Tracking Local Keystore Files

**Files:**
- Modify: `.gitignore`
- Remove from git index: `jbus.jks`

- [ ] **Step 1: Confirm the keystore is currently tracked**

Run:

```powershell
git ls-files -- jbus.jks
```

Expected:

```text
jbus.jks
```

- [ ] **Step 2: Ignore local keystore files**

In `.gitignore`, after the existing build-output patterns, add:

```gitignore
# Signing keys
*.jks
*.keystore
```

The top of `.gitignore` should include:

```gitignore
# Gradle
.gradle/
build/
local.properties

# Build outputs
*.apk
*.aab
*.dm

# Signing keys
*.jks
*.keystore
```

- [ ] **Step 3: Remove `jbus.jks` from git tracking but keep the local file**

Run:

```powershell
git rm --cached -- jbus.jks
```

Expected: Git stages `jbus.jks` as deleted, but the file remains on disk.

- [ ] **Step 4: Verify the local keystore file still exists**

Run:

```powershell
Test-Path .\jbus.jks
```

Expected:

```text
True
```

- [ ] **Step 5: Verify git status for the keystore change**

Run:

```powershell
git status --short -- .gitignore jbus.jks
```

Expected:

```text
 M .gitignore
D  jbus.jks
```

`jbus.jks` should not appear as an untracked file because `.gitignore` now ignores `*.jks`.

- [ ] **Step 6: Commit keystore tracking cleanup**

Run:

```powershell
git add -- .gitignore jbus.jks
git commit -m "build: stop tracking release keystore"
```

Expected: A commit is created containing `.gitignore` and the removal of `jbus.jks` from git tracking.

---

### Task 3: GitHub Release Workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Create the workflows directory**

Run:

```powershell
New-Item -ItemType Directory -Force .github\workflows
```

Expected: `.github\workflows` exists.

- [ ] **Step 2: Add the release workflow**

Create `.github/workflows/release.yml` with this exact content:

```yaml
name: Release

on:
  workflow_dispatch:

permissions:
  contents: write

concurrency:
  group: release-${{ github.repository }}
  cancel-in-progress: false

jobs:
  release:
    name: Build signed APK and publish GitHub Release
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v6
        with:
          fetch-depth: 0

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Validate signing secrets
        shell: bash
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        run: |
          missing=0
          for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
            if [ -z "${!name}" ]; then
              echo "::error title=Missing secret::$name is not configured"
              missing=1
            fi
          done

          if [ "$missing" -ne 0 ]; then
            exit 1
          fi

      - name: Decode release keystore
        shell: bash
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
        run: |
          keystore_path="$RUNNER_TEMP/release-keystore.jks"
          printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$keystore_path"
          echo "ORG_GRADLE_PROJECT_RELEASE_STORE_FILE=$keystore_path" >> "$GITHUB_ENV"

      - name: Build signed release APK
        shell: bash
        env:
          ORG_GRADLE_PROJECT_RELEASE_STORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ORG_GRADLE_PROJECT_RELEASE_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ORG_GRADLE_PROJECT_RELEASE_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        run: |
          chmod +x ./gradlew
          ./gradlew assembleRelease --stacktrace

      - name: Locate release APK
        id: apk
        shell: bash
        run: |
          mapfile -t apks < <(find app/build/outputs/apk/release -maxdepth 1 -type f -name 'jbus_release_v*.apk' | sort)

          if [ "${#apks[@]}" -ne 1 ]; then
            echo "::error title=Release APK lookup failed::Expected 1 release APK, found ${#apks[@]}"
            printf '%s\n' "${apks[@]}"
            exit 1
          fi

          apk_path="${apks[0]}"
          asset_name="$(basename "$apk_path")"

          if [[ ! "$asset_name" =~ ^jbus_release_v(.+)\.apk$ ]]; then
            echo "::error title=Version parse failed::Unexpected APK name: $asset_name"
            exit 1
          fi

          version_name="${BASH_REMATCH[1]}"
          tag_name="v$version_name"

          echo "apk_path=$apk_path" >> "$GITHUB_OUTPUT"
          echo "asset_name=$asset_name" >> "$GITHUB_OUTPUT"
          echo "version_name=$version_name" >> "$GITHUB_OUTPUT"
          echo "tag_name=$tag_name" >> "$GITHUB_OUTPUT"

      - name: Move release tag
        shell: bash
        env:
          TAG_NAME: ${{ steps.apk.outputs.tag_name }}
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git tag --force "$TAG_NAME" "$GITHUB_SHA"
          git push origin "refs/tags/$TAG_NAME" --force

      - name: Create or update GitHub Release
        shell: bash
        env:
          GH_TOKEN: ${{ github.token }}
          TAG_NAME: ${{ steps.apk.outputs.tag_name }}
          APK_PATH: ${{ steps.apk.outputs.apk_path }}
          ASSET_NAME: ${{ steps.apk.outputs.asset_name }}
        run: |
          if gh release view "$TAG_NAME" >/dev/null 2>&1; then
            gh release edit "$TAG_NAME" --title "$TAG_NAME" --target "$GITHUB_SHA"
          else
            gh release create "$TAG_NAME" --title "$TAG_NAME" --target "$GITHUB_SHA" --notes "Release $TAG_NAME"
          fi

          if gh release view "$TAG_NAME" --json assets --jq '.assets[].name' | grep -Fxq "$ASSET_NAME"; then
            gh release delete-asset "$TAG_NAME" "$ASSET_NAME" --yes
          fi

          gh release upload "$TAG_NAME" "$APK_PATH" --clobber
```

- [ ] **Step 3: Verify workflow content exists**

Run:

```powershell
rg -n "workflow_dispatch|ANDROID_KEYSTORE_BASE64|gh release upload|git push origin" .github\workflows\release.yml
```

Expected: Matches appear for all four patterns.

- [ ] **Step 4: Commit the workflow**

Run:

```powershell
git add -- .github/workflows/release.yml
git commit -m "ci: publish signed APK to GitHub Releases"
```

Expected: A commit is created containing only `.github/workflows/release.yml`.

---

### Task 4: Local Verification

**Files:**
- Verify: `app/build.gradle.kts`
- Verify: `.gitignore`
- Verify: `.github/workflows/release.yml`

- [ ] **Step 1: Check for whitespace errors**

Run:

```powershell
git diff --check HEAD~3..HEAD
```

Expected: No output and exit code 0.

- [ ] **Step 2: Verify Gradle configures after all changes**

Run:

```powershell
.\gradlew.bat :app:tasks --all
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run the local release build smoke test**

Run:

```powershell
.\gradlew.bat assembleRelease
```

Expected: `BUILD SUCCESSFUL` and a release APK under `app\build\outputs\apk\release\`. This local APK may be unsigned if local signing properties are not provided.

- [ ] **Step 4: Verify the keystore is no longer tracked**

Run:

```powershell
git ls-files -- jbus.jks
```

Expected: No output.

- [ ] **Step 5: Verify keystore ignore behavior**

Run:

```powershell
git check-ignore -v jbus.jks
```

Expected: Output references the `*.jks` rule in `.gitignore`.

- [ ] **Step 6: Verify final worktree status**

Run:

```powershell
git status --short
```

Expected: Only pre-existing unrelated untracked files remain. The implementation files should be committed.

---

### Task 5: GitHub Secret Setup and Release Validation

**Files:**
- No repository file changes.

- [ ] **Step 1: Move the keystore to safe local storage**

Run this from the repository root if using the current local `jbus.jks`:

```powershell
New-Item -ItemType Directory -Force D:\Secrets\JBusDriver
Copy-Item .\jbus.jks D:\Secrets\JBusDriver\release.jks
```

Expected: `D:\Secrets\JBusDriver\release.jks` exists.

- [ ] **Step 2: Generate the base64 secret value**

Run:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\Secrets\JBusDriver\release.jks"))
```

Expected: PowerShell prints one long base64 string.

- [ ] **Step 3: Add GitHub repository secrets**

In GitHub, open:

```text
Settings -> Secrets and variables -> Actions -> New repository secret
```

Add these exact repository secrets:

```text
ANDROID_KEYSTORE_BASE64      Full base64 output from Step 2
ANDROID_KEYSTORE_PASSWORD    Keystore password
ANDROID_KEY_ALIAS            Release key alias
ANDROID_KEY_PASSWORD         Key password
```

Expected: All four secrets appear in the repository Actions secrets list.

- [ ] **Step 4: Push the implementation branch**

Run:

```powershell
git push origin main
```

Expected: GitHub receives the workflow and the implementation commits.

- [ ] **Step 5: Run the release workflow**

In GitHub, open:

```text
Actions -> Release -> Run workflow
```

Select `main`, then run without entering a version.

Expected: The workflow completes successfully.

- [ ] **Step 6: Verify the GitHub Release**

In GitHub, open:

```text
Releases
```

Expected:

```text
Release: v1.<yyyyMMdd>
Asset:   jbus_release_v1.<yyyyMMdd>.apk
```

- [ ] **Step 7: Verify same-day replacement behavior**

Run the same workflow again from `main`.

Expected:

1. The workflow succeeds.
2. The `v1.<yyyyMMdd>` tag points to the latest workflow commit.
3. The Release contains the newest `jbus_release_v1.<yyyyMMdd>.apk` asset.
