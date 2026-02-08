$mavenUrl = 'https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip'
$downloadPath = "$env:TEMP\maven.zip"
$installPath = 'C:\tools'
$mavenBin = 'C:\tools\apache-maven-3.9.9\bin'

Write-Host 'Downloading Maven 3.9.9...'
Invoke-WebRequest -Uri $mavenUrl -OutFile $downloadPath

Write-Host 'Extracting...'
New-Item -ItemType Directory -Force -Path $installPath | Out-Null
Expand-Archive -Path $downloadPath -DestinationPath $installPath -Force

Write-Host 'Setting user PATH...'
$currentPath = [System.Environment]::GetEnvironmentVariable('PATH', [System.EnvironmentVariableTarget]::User)
if ($currentPath -notlike "*$mavenBin*") {
    [System.Environment]::SetEnvironmentVariable('PATH', "$currentPath;$mavenBin", [System.EnvironmentVariableTarget]::User)
}

Write-Host 'Setting JAVA_HOME to JDK 11...'
$jdk11 = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Filter 'jdk-11*' -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk11) {
    [System.Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk11.FullName, [System.EnvironmentVariableTarget]::User)
    Write-Host "JAVA_HOME = $($jdk11.FullName)"
}

Write-Host "Maven bin: $mavenBin"
& "$mavenBin\mvn.cmd" -version
Write-Host 'Done!'
