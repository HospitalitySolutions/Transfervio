param([switch]$Unfamiliar)
# Minimal standards-based OpenXML workbook; fictional rows match clients-sl.csv.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
$destination = Join-Path (Split-Path -Parent $PSScriptRoot) 'fixtures\synthetic\clients-en.xlsx'
if($Unfamiliar){$destination=Join-Path (Split-Path -Parent $PSScriptRoot) '.local-data\unfamiliar.xlsx'}
$parts = @{
    '[Content_Types].xml' = '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>'
    '_rels/.rels' = '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'
    'xl/workbook.xml' = '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Synthetic clients" sheetId="1" r:id="rId1"/></sheets></workbook>'
    'xl/_rels/workbook.xml.rels' = '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>'
}
$rows = ,@('id','Given name','Surname','Email address','Mobile')
if($Unfamiliar){$rows=,@('Roster number','Given label','Family label','Electronic endpoint','Contact number')}
foreach ($n in 1..5) { $rows += ,@("demo-c$n","Demo$n",'Example',"demo$n@example.test","+3864000000$n") }
$xml = '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'
for ($r=0; $r -lt $rows.Count; $r++) {
    $xml += '<row r="' + ($r+1) + '">'
    for ($c=0; $c -lt $rows[$r].Count; $c++) {
        $cell = [string][char](65+$c) + ($r+1)
        $xml += '<c r="' + $cell + '" t="inlineStr"><is><t>' + [Security.SecurityElement]::Escape($rows[$r][$c]) + '</t></is></c>'
    }
    $xml += '</row>'
}
$parts['xl/worksheets/sheet1.xml'] = $xml + '</sheetData></worksheet>'
$stream = [IO.File]::Open($destination,[IO.FileMode]::Create)
$archive = [IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($name in ($parts.Keys | Sort-Object)) {
        $entry = $archive.CreateEntry($name)
        $entry.LastWriteTime = [DateTimeOffset]'2026-01-01T00:00:00Z'
        $writer = [IO.StreamWriter]::new($entry.Open(),[Text.UTF8Encoding]::new($false))
        try { $writer.Write($parts[$name]) } finally { $writer.Dispose() }
    }
} finally { $archive.Dispose(); $stream.Dispose() }
Write-Host "Generated synthetic $([IO.Path]::GetFileName($destination)) (five clients)."
