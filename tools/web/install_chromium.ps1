param([string]$GameDirectory=$PSScriptRoot,[string]$NativeArchive='')
$ErrorActionPreference='Stop'
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
$game=[IO.Path]::GetFullPath($GameDirectory)
$runtime=Join-Path $game 'ochd-web-runtime'
if(!(Test-Path -LiteralPath (Join-Path $game 'options.txt'))){throw 'Extract this package into the Minecraft game directory (the directory containing options.txt and mods) first.'}
$instance=if((Split-Path $game -Leaf) -eq '.minecraft'){Split-Path $game -Parent}else{$game}
$running=Get-CimInstance Win32_Process | Where-Object {($_.Name -match '^javaw?\.exe$|^jcef_helper\.exe$') -and $_.CommandLine -and $_.CommandLine.Replace('\','/').ToLowerInvariant().Contains($instance.Replace('\','/').ToLowerInvariant())}
if($running){throw 'Close this Minecraft instance before installing Chromium.'}
$manifest=Get-Content -Raw -LiteralPath (Join-Path $runtime 'setup-files.json') | ConvertFrom-Json
function Verify-Files($base,$files){
 foreach($entry in $files.PSObject.Properties){
  if($entry.Name -match '(^/|^[A-Za-z]:|(^|/)\.\.(/|$))'){throw 'Invalid manifest path'}
  $path=Join-Path $base $entry.Name
  if(!(Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.Value){return $false}
 }
 return $true
}
if(!(Verify-Files $game $manifest.client)){throw 'Client package files are incomplete or damaged. Extract the package again.'}
$target=Join-Path $runtime 'native'
$stage=$null;$oldNative=$null;$movedMods=@()
try {
 if(!(Verify-Files $target $manifest.native)){
  if(!(Get-Command tar.exe -ErrorAction SilentlyContinue)){throw 'Windows tar.exe is required (Windows 10/11).'}
  $stage=Join-Path $runtime ('.cef-stage-'+[Guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Path $stage | Out-Null
  $archive=Join-Path $stage 'jcef-natives.jar'
  $deps=Get-Content -Raw -LiteralPath (Join-Path $runtime 'cef-dependencies.json') | ConvertFrom-Json
  $dep=$deps.'jcef-natives.jar'
  if($NativeArchive){Copy-Item -LiteralPath $NativeArchive -Destination $archive}else{
   if(-not $dep.source.StartsWith('https://repo.maven.apache.org/')){throw 'Unexpected dependency source'}
   Write-Host 'Downloading the pinned official Chromium/CEF native bundle (about 162 MB)...'
   Invoke-WebRequest -UseBasicParsing -Uri $dep.source -OutFile $archive -TimeoutSec 900
  }
  if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $dep.sha256){throw 'Native archive SHA256 mismatch; nothing installed.'}
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip=[IO.Compression.ZipFile]::OpenRead($archive)
  try {
   $entries=@($zip.Entries | Where-Object {$_.FullName.EndsWith('.tar.gz')})
   if($entries.Count -ne 1){throw 'Unexpected native archive layout'}
   $tar=Join-Path $stage 'native.tar.gz'
   $archiveInput=$entries[0].Open();$archiveOutput=[IO.File]::Create($tar)
   try{$archiveInput.CopyTo($archiveOutput)}finally{$archiveInput.Dispose();$archiveOutput.Dispose()}
  }finally{$zip.Dispose()}
  $incoming=Join-Path $stage 'native';New-Item -ItemType Directory -Path $incoming | Out-Null
  & tar.exe -xzf $tar -C $incoming
  if($LASTEXITCODE -ne 0 -or !(Verify-Files $incoming $manifest.native)){throw 'Native extraction verification failed; nothing installed.'}
  # Both move targets are inside this game's runtime directory.
  if(Test-Path -LiteralPath $target){$oldNative=Join-Path $runtime ('native-backup-'+[Guid]::NewGuid().ToString('N'));Move-Item -LiteralPath $target -Destination $oldNative}
  Move-Item -LiteralPath $incoming -Destination $target
 }
 $mods=Join-Path $game 'mods';$backup=Join-Path $runtime ('old-mods-'+[Guid]::NewGuid().ToString('N'))
 foreach($mod in Get-ChildItem -LiteralPath $mods -Filter 'oc-hd-panel-*.jar' -File){
  if($mod.Name -ne 'oc-hd-panel-0.4.0-alpha8-chromium.jar'){
   New-Item -ItemType Directory -Path $backup -Force | Out-Null
   $saved=Join-Path $backup $mod.Name;Move-Item -LiteralPath $mod.FullName -Destination $saved
   $movedMods+=@{source=$mod.FullName;saved=$saved}
  }
 }
 Write-Host 'SUCCESS: Chromium is installed and verified. Start Minecraft now.'
 Write-Host 'Server: replace its OCHD JAR with alpha8 and restart; no browser runtime on the server.'
}catch{
 foreach($mod in $movedMods){if(Test-Path -LiteralPath $mod.saved){Move-Item -LiteralPath $mod.saved -Destination $mod.source}}
 if($oldNative -and !(Test-Path -LiteralPath $target)){Move-Item -LiteralPath $oldNative -Destination $target}
 throw
}finally{
 if($stage){
  $absolute=[IO.Path]::GetFullPath($stage)
  if($absolute.StartsWith([IO.Path]::GetFullPath($runtime)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -and (Split-Path $absolute -Leaf).StartsWith('.cef-stage-')){Remove-Item -LiteralPath $absolute -Recurse -Force}
 }
}
