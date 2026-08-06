$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path "$root\pom.xml")) { throw "pom.xml not found near $PSScriptRoot" }
Set-Location $root

if (Test-Path "C:\Users\guven\.jdks\ms-25.0.3") {
  $env:JAVA_HOME = "C:\Users\guven\.jdks\ms-25.0.3"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

& "$root\mvnw.cmd" -q dependency:build-classpath "-Dmdep.pathSeparator=;" "-Dmdep.outputFile=target\classpath.txt"
$jars = (Get-Content "$root\target\classpath.txt" -Raw).Split(';') | Where-Object { $_ -and (Test-Path $_.Trim()) }
$m2 = (Resolve-Path "$env:USERPROFILE\.m2\repository").Path -replace '\\', '/'

New-Item -ItemType Directory -Force -Path "$root\.idea\libraries" | Out-Null
Get-ChildItem "$root\.idea\libraries\Maven__*.xml" -ErrorAction SilentlyContinue | Remove-Item -Force

$libOrder = New-Object System.Collections.Generic.List[string]
$libOrder.Add('    <orderEntry type="inheritedJdk" />')
$libOrder.Add('    <orderEntry type="sourceFolder" forTests="false" />')

foreach ($jarRaw in $jars) {
  $jar = $jarRaw.Trim() -replace '\\', '/'
  if ($jar -notlike "$m2/*") { continue }
  $rel = $jar.Substring($m2.Length + 1)
  $parts = $rel.Split('/')
  if ($parts.Length -lt 4) { continue }
  $version = $parts[-2]
  $artifact = $parts[-3]
  $groupPath = ($parts[0..($parts.Length - 4)] -join '/')
  $groupId = $groupPath -replace '/', '.'
  $libName = "Maven: ${groupId}:${artifact}:${version}"
  $fileSafe = ("Maven__" + ($groupId -replace '\.', '_') + "_" + ($artifact -replace '[\.-]', '_') + "_" + ($version -replace '[\.-]', '_'))
  $libXml = @"
<component name="libraryTable">
  <library name="$libName">
    <CLASSES>
      <root url="jar://`$MAVEN_REPOSITORY`$/$rel!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES />
  </library>
</component>
"@
  $libXml = $libXml.Replace('`$MAVEN_REPOSITORY`$', '$MAVEN_REPOSITORY$')
  [System.IO.File]::WriteAllText("$root\.idea\libraries\$fileSafe.xml", $libXml)
  $libOrder.Add("    <orderEntry type=`"library`" name=`"$libName`" level=`"project`" />")
}

$imlBody = @"
<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" LANGUAGE_LEVEL="JDK_21">
    <output url="file://`$MODULE_DIR`$/target/classes" />
    <output-test url="file://`$MODULE_DIR`$/target/test-classes" />
    <content url="file://`$MODULE_DIR`$">
      <sourceFolder url="file://`$MODULE_DIR`$/src/main/java" isTestSource="false" />
      <sourceFolder url="file://`$MODULE_DIR`$/src/main/resources" type="java-resource" />
      <sourceFolder url="file://`$MODULE_DIR`$/src/test/java" isTestSource="true" />
      <sourceFolder url="file://`$MODULE_DIR`$/src/test/resources" type="java-test-resource" />
      <sourceFolder url="file://`$MODULE_DIR`$/target/generated-sources/annotations" isTestSource="false" generated="true" />
      <sourceFolder url="file://`$MODULE_DIR`$/target/generated-test-sources/test-annotations" isTestSource="true" generated="true" />
      <excludeFolder url="file://`$MODULE_DIR`$/target" />
    </content>
$($libOrder -join "`n")
  </component>
</module>
"@
$imlBody = $imlBody.Replace('`$MODULE_DIR`$', '$MODULE_DIR$')

$imlPath = "$root\algoryqr-service.iml"
if (Test-Path $imlPath) { attrib -R $imlPath | Out-Null }
[System.IO.File]::WriteAllText($imlPath, $imlBody)
attrib +R $imlPath | Out-Null

[System.IO.File]::WriteAllText("$root\.idea\modules.xml", @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectModuleManager">
    <modules>
      <module fileurl="file://`$PROJECT_DIR`$/algoryqr-service.iml" filepath="`$PROJECT_DIR`$/algoryqr-service.iml" />
    </modules>
  </component>
</project>
"@.Replace('`$PROJECT_DIR`$', '$PROJECT_DIR$'))

[System.IO.File]::WriteAllText("$root\.idea\misc.xml", @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ExternalStorageConfigurationManager" enabled="false" />
  <component name="MavenProjectsManager">
    <option name="originalFiles">
      <list>
        <option value="`$PROJECT_DIR`$/pom.xml" />
      </list>
    </option>
  </component>
  <component name="ProjectRootManager" version="2" languageLevel="JDK_21" default="true" project-jdk-name="ms-25" project-jdk-type="JavaSDK" />
</project>
"@.Replace('`$PROJECT_DIR`$', '$PROJECT_DIR$'))

$extRoot = Join-Path $env:LOCALAPPDATA "JetBrains\IntelliJIdea2026.2\projects\qr-service.38f2c699"
foreach ($dir in @("external_build_system", "project-model-cache")) {
  $p = Join-Path $extRoot $dir
  if (Test-Path $p) { Remove-Item -Recurse -Force $p }
}

Write-Output "Synced $($jars.Count) Maven libraries into IntelliJ. Close/reopen project, then Rebuild. Do not click Maven Reload."
