$lines = Get-Content 'D:\Workspace\AiDev\003-LumeCardWithOpenCode\shared\src\commonMain\resources\help\zh-CN\articles.json'
for ($ln = 0; $ln -lt $lines.Count; $ln++) {
    $line = $lines[$ln]
    $inString = $false
    for ($idx = 0; $idx -lt $line.Length; $idx++) {
        $c = $line[$idx]
        if ($c -eq '"') {
            if ($inString) {
                Write-Host ("Line " + ($ln+1) + " Pos " + $idx + ": UNESCAPED ASCII `" inside string")
            }
            $inString = !$inString
        }
    }
}
