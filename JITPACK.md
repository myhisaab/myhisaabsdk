# Releasing myhisaabsdk on JitPack

This is a standalone, single-module Android library. JitPack serves it as
`com.github.myhisaab:myhisaabsdk:<tag>`.

> The library was authored in an environment that could not compile it. Step 0 —
> building once in Android Studio — is required before you tag.

## 0. Verify it compiles

Open this folder in Android Studio, let Gradle sync, then:

```
./gradlew assembleRelease
./gradlew publishToMavenLocal   # same task JitPack runs; proves the publication works
```

## 1. Create the GitHub home (one time)

1. Create a GitHub **organization or user named `myhisaab`** (github.com/myhisaab
   must be yours — it is currently unclaimed).
2. Under it, create an **empty repo named `myhisaabsdk`** (no README/license, so
   the first push is clean). Repo owner + name are what produce the
   `com.github.myhisaab:myhisaabsdk` coordinate — don't rename them.

## 2. Push

```
cd myhisaabsdk
git init
git add .
git commit -m "MyHisaab Android SDK 1.0.0"
git branch -M main
git remote add origin https://github.com/myhisaab/myhisaabsdk.git
git push -u origin main
```

## 3. Tag the release

Keep the tag equal to `version` in `build.gradle.kts` (currently `1.1.0`).

```
git tag 1.0.0
git push origin 1.0.0
```

## 4. Build on JitPack

Open `https://jitpack.io/#myhisaab/myhisaabsdk`, select tag `1.0.0`, click
**Get it**, and watch the log (first build takes a few minutes).

## 5. Consume it

Consumer `settings.gradle.kts`:

```kotlin
maven { url = uri("https://jitpack.io") }
```

Consumer `build.gradle.kts`:

```kotlin
implementation("com.github.myhisaab:myhisaabsdk:1.0.0")
```

## Releasing a new version later

1. Bump `version` in `build.gradle.kts`.
2. Commit, then `git tag <new-version>` and push the tag.
3. Point consumers at the new tag.

## First-build risk

`compileSdk = 37` / `AGP 9.2.1` are very new. `jitpack.yml` installs
`platforms;android-37`, but if JitPack's image can't fetch it, lower `compileSdk`
in `build.gradle.kts` and re-tag. The code uses no API above ~28, so a lower
`compileSdk` (e.g. 35/36) is safe.
