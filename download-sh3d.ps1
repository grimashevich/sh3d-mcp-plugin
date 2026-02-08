$url = 'https://sourceforge.net/projects/sweethome3d/files/SweetHome3D/SweetHome3D-7.5/SweetHome3D-7.5.jar/download'
$dest = 'C:\Users\kgrim\projects\SH3D\plugin\lib\SweetHome3D.jar'

Write-Host 'Downloading SweetHome3D 7.5 JAR...'
# SourceForge redirects, use -MaximumRedirection
Invoke-WebRequest -Uri $url -OutFile $dest -MaximumRedirection 10 -UserAgent 'Mozilla/5.0'
Write-Host "Downloaded: $dest"
Write-Host "Size: $((Get-Item $dest).Length / 1MB) MB"
