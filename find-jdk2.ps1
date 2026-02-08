# Check winget result
winget list --id EclipseAdoptium.Temurin.11.JDK 2>$null
Write-Host '---'
# Broad search for jdk-11
Get-ChildItem 'C:\Program Files' -Recurse -Filter 'javac.exe' -ErrorAction SilentlyContinue -Depth 3 | ForEach-Object { Write-Host $_.FullName }
Write-Host '---'
Get-ChildItem 'C:\Program Files (x86)' -Recurse -Filter 'javac.exe' -ErrorAction SilentlyContinue -Depth 3 | ForEach-Object { Write-Host $_.FullName }
