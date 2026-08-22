param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('patch', 'minor', 'major')]
    [string]$Bump,

    [string]$Path = 'app/build.gradle.kts'
)

$ErrorActionPreference = 'Stop'
$text = [IO.File]::ReadAllText($Path)
$codePattern = [regex]'versionCode\s*=\s*(\d+)'
$namePattern = [regex]'versionName\s*=\s*"([^"]+)"'
$codeMatch = $codePattern.Match($text)
$nameMatch = $namePattern.Match($text)

if (-not $codeMatch.Success -or -not $nameMatch.Success) {
    throw "Unable to find Android version fields in $Path."
}

$parts = $nameMatch.Groups[1].Value.Split('.')
$major = [int]$parts[0]
$minor = if ($parts.Length -gt 1) { [int]$parts[1] } else { 0 }
$patch = if ($parts.Length -gt 2) { [int]$parts[2] } else { 0 }

switch ($Bump) {
    'major' { $major++; $minor = 0; $patch = 0 }
    'minor' { $minor++; $patch = 0 }
    'patch' { $patch++ }
}

$nextVersion = "$major.$minor.$patch"
$nextCode = [int]$codeMatch.Groups[1].Value + 1
$text = $codePattern.Replace($text, "versionCode = $nextCode", 1)
$text = $namePattern.Replace($text, "versionName = `"$nextVersion`"", 1)
[IO.File]::WriteAllText($Path, $text, [Text.UTF8Encoding]::new($false))

Write-Output $nextVersion
