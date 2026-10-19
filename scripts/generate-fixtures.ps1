# Generates only fictional test records and screenshot layouts. No external services.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root 'fixtures\synthetic'
New-Item -ItemType Directory -Force $dir | Out-Null
$clients = @(1..5 | ForEach-Object { @{source_id="demo-c$_";first_name="Demo$_";last_name='Example';display_name="Demo$_ Example";email="demo$_@example.test";phone="+3864000000$_";language='sl'} })
$appointments = @(1..3 | ForEach-Object { @{source_id="demo-a$_";client_source_id="demo-c$_";start="2026-11-0$($_ + 1)T10:00";duration_minutes='60';timezone='Europe/Ljubljana';status='CONFIRMED';source_service='Consultation';source_employee='Demo Specialist'} })
$csv = "id,Ime,Priimek,E-mail,Telefon`n" + (($clients | ForEach-Object { "$($_.source_id),$($_.first_name),$($_.last_name),$($_.email),$($_.phone)" }) -join "`n")
[IO.File]::WriteAllText((Join-Path $dir 'clients-sl.csv'),$csv,(New-Object Text.UTF8Encoding($false)))
@{clients=$clients;appointments=$appointments} | ConvertTo-Json -Depth 20 | Set-Content (Join-Path $dir 'complete-migration.json') -Encoding utf8
$manifest = @{}
for($i=1;$i -le 20;$i++) {
    $bitmap = New-Object Drawing.Bitmap(1200,640)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = 'AntiAlias'; $graphics.TextRenderingHint = 'AntiAliasGridFit'
    $graphics.Clear([Drawing.ColorTranslator]::FromHtml('#f5f7f4'))
    $dark = New-Object Drawing.SolidBrush([Drawing.ColorTranslator]::FromHtml('#173b31'))
    $muted = New-Object Drawing.SolidBrush([Drawing.ColorTranslator]::FromHtml('#62766b'))
    $white = New-Object Drawing.SolidBrush([Drawing.Color]::White)
    $title = New-Object Drawing.Font('Segoe UI',26,[Drawing.FontStyle]::Bold)
    $body = New-Object Drawing.Font('Segoe UI',16)
    $graphics.FillRectangle($dark,0,0,1200,100)
    $graphics.DrawString("SYNTHETIC DEMO  /  Layout $i",$title,$white,32,25)
    if($i -le 8) {
        $records=@{clients=$clients}
        $graphics.DrawString('Client directory · Fictional data',$title,$dark,32,125)
        $y=210
        foreach($client in $clients) { $graphics.FillRectangle($white,24,$y-8,1152,60);$graphics.DrawString("$($client.source_id)    $($client.display_name)    $($client.email)    $($client.phone)",$body,$dark,40,$y);$y+=76 }
    } elseif($i -le 16) {
        $records=@{appointments=$appointments}
        $graphics.DrawString('Appointments · Europe/Ljubljana',$title,$dark,32,125)
        $y=220
        foreach($appointment in $appointments) { $graphics.DrawString("$($appointment.source_id)  |  $($appointment.client_source_id)  |  $($appointment.start)  |  60 min",$body,$dark,40,$y);$graphics.DrawString('Consultation · Demo Specialist · CONFIRMED',$body,$muted,40,($y+36));$y+=115 }
    } else {
        $record=@{source_id="incomplete-$i";client_source_id='demo-c1';start='03/04/2026 10:00';source_service='Consultation';source_employee='Demo Specialist';status='CONFIRMED'}
        $records=@{appointments=@($record)}
        $graphics.DrawString('Calendar · cropped source',$title,$dark,32,125)
        $graphics.FillRectangle($white,40,220,520,270)
        $graphics.DrawString('03/04/2026   10:00',$title,$dark,65,250)
        $graphics.DrawString("demo-c1 · Consultation`nDemo Specialist`nDuration not visible",$body,$muted,65,320)
    }
    $graphics.DrawString('Test fixture only. Not a real client export.',$body,$muted,32,580)
    $file=Join-Path $dir ('screenshot-{0:d2}.png' -f $i)
    $bitmap.Save($file,[Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose();$bitmap.Dispose();$title.Dispose();$body.Dispose();$dark.Dispose();$muted.Dispose();$white.Dispose()
    $hash=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower()
    $manifest[$hash]=@{file=[IO.Path]::GetFileName($file);layout=$i;records=$records}
}
$manifest | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $dir 'ground-truth.json') -Encoding utf8
Copy-Item (Join-Path $dir 'ground-truth.json') (Join-Path $root 'apps\api\src\main\resources\synthetic-images.json') -Force
& "$PSScriptRoot\generate-xlsx-fixture.ps1"
$hashes=@(Get-ChildItem $dir -File | Where-Object {$_.Extension -in '.png','.jpg','.csv','.xlsx' -or $_.Name -eq 'complete-migration.json'} | ForEach-Object {(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower()})
$hashes | ConvertTo-Json | Set-Content (Join-Path $root 'apps\api\src\main\resources\synthetic-inputs.json') -Encoding utf8
Write-Host 'Generated 20 labeled synthetic images, CSV, XLSX and JSON fixtures.'
