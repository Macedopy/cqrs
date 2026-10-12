# ============================================================
#  Starts the application with a clean build.
#
#  Why "clean"? The VS Code Java extension (Eclipse JDT) may write
#  broken .class files into target/classes before Maven starts.
#  Running clean removes them. If you reload VS Code and it honours
#  .vscode/settings.json (java.project.outputPath = target/ide-classes),
#  the clean becomes unnecessary but harmless.
#
#  Usage: powershell -ExecutionPolicy Bypass -File scripts/run.ps1
# ============================================================

$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"

Write-Host "Cleaning target/classes..." -ForegroundColor Cyan
.\mvnw.cmd -q clean 2>&1 | Out-Null

Write-Host "Starting the application..." -ForegroundColor Cyan
.\mvnw.cmd spring-boot:run