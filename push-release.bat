@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo CodeGate Android release publisher
echo.

where git.exe >nul 2>nul
if errorlevel 1 (
    echo Git was not found on PATH.
    exit /b 1
)

where powershell.exe >nul 2>nul
if errorlevel 1 (
    echo Windows PowerShell was not found on PATH.
    exit /b 1
)

for /f "delims=" %%B in ('git branch --show-current') do set "BRANCH=%%B"
if not defined BRANCH (
    echo Unable to determine the current Git branch.
    exit /b 1
)
if /i not "%BRANCH%"=="main" (
    echo Releases must be created from main. Current branch: %BRANCH%
    exit /b 1
)

git remote get-url origin >nul 2>nul
if errorlevel 1 (
    echo This repository does not have an origin remote.
    exit /b 1
)

for /f "tokens=3" %%V in ('findstr /c:"versionName =" app\build.gradle.kts') do set "CURRENT_VERSION=%%~V"
if not defined CURRENT_VERSION (
    echo Unable to read versionName from app\build.gradle.kts.
    exit /b 1
)

echo Current version: %CURRENT_VERSION%
echo Current changes that will be included in the release commit:
git status --short
echo.
echo Choose the version increment:
echo   [P] Patch  - fixes and small changes
echo   [M] Minor  - backward-compatible features
echo   [J] Major  - breaking changes
echo   [X] Cancel
choice /C PMJX /N /M "Selection: "
if errorlevel 4 exit /b 0
if errorlevel 3 set "BUMP=major"
if errorlevel 2 set "BUMP=minor"
if errorlevel 1 set "BUMP=patch"

echo.
echo This will include ALL files shown above, increment the %BUMP% version,
echo run the Android release checks, create a release commit and tag, then
echo push both to origin. Pushing the tag triggers the GitHub Actions build.
choice /C YN /N /M "Continue? [Y/N]: "
if errorlevel 2 exit /b 0

echo.
echo Checking the working tree...
git diff --check
if errorlevel 1 (
    echo Fix the errors above before publishing.
    exit /b 1
)

if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

echo.
echo Running Android release checks...
call gradlew.bat --no-daemon testDebugUnitTest lintRelease assembleRelease
if errorlevel 1 (
    echo Release checks failed. No version change was made.
    exit /b 1
)

echo.
echo Updating Android package versions...
set "VERSION_SCRIPT=%TEMP%\codegate-android-version-%RANDOM%-%RANDOM%.ps1"
(
    echo $ErrorActionPreference = 'Stop'
    echo $path = 'app/build.gradle.kts'
    echo $text = [IO.File]::ReadAllText($path^)
    echo $codePattern = [regex]'versionCode\s*=\s*(\d+)'
    echo $namePattern = [regex]'versionName\s*=\s*"([^"]+)"'
    echo $codeMatch = $codePattern.Match($text^)
    echo $nameMatch = $namePattern.Match($text^)
    echo if (-not $codeMatch.Success -or -not $nameMatch.Success^) { throw 'Unable to find Android version fields.' }
    echo $parts = $nameMatch.Groups[1].Value.Split('.'^)
    echo $major = [int]$parts[0]
    echo $minor = if ($parts.Length -gt 1^) { [int]$parts[1] } else { 0 }
    echo $patch = if ($parts.Length -gt 2^) { [int]$parts[2] } else { 0 }
    echo switch ('%BUMP%'^) {
    echo     'major' { $major++; $minor = 0; $patch = 0 }
    echo     'minor' { $minor++; $patch = 0 }
    echo     default { $patch++ }
    echo }
    echo $next = "$major.$minor.$patch"
    echo $nextCode = [int]$codeMatch.Groups[1].Value + 1
    echo $text = $codePattern.Replace($text, "versionCode = $nextCode", 1^)
    echo $text = $namePattern.Replace($text, "versionName = `"$next`"", 1^)
    echo [IO.File]::WriteAllText($path, $text, [Text.UTF8Encoding]::new($false^)^)
    echo Write-Output $next
) > "%VERSION_SCRIPT%"

for /f "delims=" %%V in ('powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%VERSION_SCRIPT%"') do set "NEW_VERSION=%%V"
set "VERSION_RESULT=%ERRORLEVEL%"
del /q "%VERSION_SCRIPT%" >nul 2>nul
if not "%VERSION_RESULT%"=="0" (
    echo Version update failed. Nothing was committed.
    exit /b 1
)
if not defined NEW_VERSION (
    echo The version changed, but the new version could not be read. Nothing was committed.
    exit /b 1
)
set "RELEASE_TAG=v%NEW_VERSION%"

git rev-parse -q --verify "refs/tags/%RELEASE_TAG%" >nul 2>nul
if not errorlevel 1 (
    echo Tag %RELEASE_TAG% already exists. Nothing was committed.
    exit /b 1
)

echo.
echo Creating release commit %RELEASE_TAG%...
git add -A
git commit -m "Release %RELEASE_TAG%"
if errorlevel 1 (
    echo Commit failed. The version change remains in the working tree for inspection.
    exit /b 1
)

git tag -a "%RELEASE_TAG%" -m "CodeGate Android %RELEASE_TAG%"
if errorlevel 1 (
    echo Tag creation failed. The release commit exists locally but was not pushed.
    exit /b 1
)

echo.
echo Pushing main and %RELEASE_TAG% to origin...
git push --atomic origin main "%RELEASE_TAG%"
if errorlevel 1 (
    echo Push failed. The release commit and tag remain local and can be retried with:
    echo   git push --atomic origin main %RELEASE_TAG%
    exit /b 1
)

echo.
echo Published %RELEASE_TAG%.
echo GitHub Actions will now build and publish the unsigned Android APK.
exit /b 0
