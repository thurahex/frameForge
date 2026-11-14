# First-run setup for machines without an installed Gradle Wrapper.
# Downloads only the official Gradle 8.13 Wrapper JAR and validates its published SHA-256.
$ErrorActionPreference = 'Stop'
$jarPath = Join-Path $PSScriptRoot 'gradle\wrapper\gradle-wrapper.jar'
$uri = 'https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar'
$expected = '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f'
if (-not (Test-Path $jarPath)) {
    Write-Host 'Downloading the official Gradle 8.13 Wrapper JAR...'
    Invoke-WebRequest -Uri $uri -OutFile $jarPath -UseBasicParsing
}
$actual = (Get-FileHash $jarPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expected) {
    Remove-Item $jarPath -ErrorAction SilentlyContinue
    throw "Wrapper integrity check failed. Expected $expected but got $actual"
}
Write-Host 'Gradle Wrapper verified. You can open this folder in Android Studio.'
