$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
$env:PATH = "C:\tools\apache-maven-3.9.9\bin;$env:JAVA_HOME\bin;$env:PATH"
Set-Location C:\Users\kgrim\projects\SH3D\plugin
mvn test -Dtest="com.sh3d.mcp.protocol.**" -DfailIfNoTests=false 2>&1
