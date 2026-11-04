param(
    [string]$Agent,
    [ValidateSet('user','project')][string]$Scope = 'user',
    [string]$Project,
    [string]$Dest,
    [switch]$Uninstall,
    [switch]$DryRun,
    [switch]$Yes,
    [switch]$ListAgents
)
$ErrorActionPreference = 'Stop'
$installerArgs = @((Join-Path $PSScriptRoot 'scripts/install.py'), '--scope', $Scope)
if ($Agent) { $installerArgs += @('--agent', $Agent) }
if ($Project) { $installerArgs += @('--project', $Project) }
if ($Dest) { $installerArgs += @('--dest', $Dest) }
if ($Uninstall) { $installerArgs += '--uninstall' }
if ($DryRun) { $installerArgs += '--dry-run' }
if ($Yes) { $installerArgs += '--yes' }
if ($ListAgents) { $installerArgs += '--list-agents' }
if (Get-Command python3 -ErrorAction SilentlyContinue) { & python3 @installerArgs }
elseif (Get-Command py -ErrorAction SilentlyContinue) { & py -3 @installerArgs }
elseif (Get-Command python -ErrorAction SilentlyContinue) { & python @installerArgs }
else { throw 'Python 3.9+ required. Copy the skill folder manually; see README.md.' }
exit $LASTEXITCODE
