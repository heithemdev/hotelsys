$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User') }
    if (-not $env:JAVA_HOME) { throw 'JDK 21 requis : configurez JAVA_HOME.' }
    & .\mvnw.cmd -B -ntp test-compile dependency:build-classpath '-Dmdep.outputFile=target/ui-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Compilation impossible.' }
    $classpath = (Get-Content -LiteralPath 'target/ui-classpath.txt' -Raw).Trim()
    $modules = (($classpath -split ';') | Where-Object { $_ -match 'javafx-(base|graphics|controls|fxml)-[^;]+-win\.jar$' }) -join ';'
    & (Join-Path $env:JAVA_HOME 'bin\java.exe') --module-path $modules --add-modules javafx.controls,javafx.fxml '-Dprism.order=sw' -cp "target\test-classes;target\classes;$classpath" com.hotelsys.UiCheck
    if ($LASTEXITCODE -ne 0) { throw 'Échec du passage graphique.' }
} finally { Pop-Location }
