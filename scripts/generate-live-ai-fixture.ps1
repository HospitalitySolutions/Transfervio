# A previously unseen fictional image, generated locally for explicitly authorized testing.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=Split-Path -Parent $PSScriptRoot
$bitmap=[Drawing.Bitmap]::new(1000,420)
$graphics=[Drawing.Graphics]::FromImage($bitmap)
$font=[Drawing.Font]::new('Segoe UI',22)
try {
    $graphics.Clear([Drawing.Color]::White)
    $text="SYNTHETIC MIGRATION ACCEPTANCE TEST`nClient ID: live-client-1`nFirst name: Nora`nLast name: Example`nEmail: nora@example.test`nFictional test data only."
    $graphics.DrawString($text,$font,[Drawing.Brushes]::Black,30,25)
    $bitmap.Save((Join-Path $root '.local-data\unseen-synthetic.png'),[Drawing.Imaging.ImageFormat]::Png)
} finally { $font.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
Write-Host 'Created unseen synthetic image locally.'
