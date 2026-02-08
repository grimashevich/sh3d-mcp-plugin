Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue | ForEach-Object { Write-Host $_.FullName }
Write-Host '---'
Get-ChildItem 'C:\Program Files' -Filter 'jdk*' -Directory -ErrorAction SilentlyContinue | ForEach-Object { Write-Host $_.FullName }
Write-Host '---'
Get-Command java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
