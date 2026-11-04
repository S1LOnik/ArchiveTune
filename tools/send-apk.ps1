param(
    [string]$ApkPath = "app\build\outputs\apk\fossMobileUniversal\debug\app-foss-mobile-universal-debug.apk",
    [string]$Caption = "ArchiveTune Build $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
)

$token = "8957047064:AAEreEUag7IYIH6hjK3tRH0THQGPsPJk7bk"
$chatId = "1194291689"

if (-not (Test-Path $ApkPath)) {
    Write-Error "APK file not found at: $ApkPath"
    exit 1
}

$fileItem = Get-Item $ApkPath
Write-Host "Uploading $($fileItem.Name) ($([math]::Round($fileItem.Length / 1MB, 2)) MB) to Telegram..."

$uri = "https://api.telegram.org/bot$token/sendDocument"

$boundary = [System.Guid]::NewGuid().ToString()
$LF = "`r`n"

$fileBytes = [System.IO.File]::ReadAllBytes($fileItem.FullName)

$bodyLines = @(
    "--$boundary",
    "Content-Disposition: form-data; name=`"chat_id`"",
    "",
    "$chatId",
    "--$boundary",
    "Content-Disposition: form-data; name=`"caption`"",
    "",
    "$Caption",
    "--$boundary",
    "Content-Disposition: form-data; name=`"document`"; filename=`"$($fileItem.Name)`"",
    "Content-Type: application/vnd.android.package-archive",
    ""
)

$headerText = ($bodyLines -join $LF) + $LF
$headerBytes = [System.Text.Encoding]::UTF8.GetBytes($headerText)
$footerText = "$LF--$boundary--$LF"
$footerBytes = [System.Text.Encoding]::UTF8.GetBytes($footerText)

$totalBytes = [byte[]]::new($headerBytes.Length + $fileBytes.Length + $footerBytes.Length)
[System.Buffer]::BlockCopy($headerBytes, 0, $totalBytes, 0, $headerBytes.Length)
[System.Buffer]::BlockCopy($fileBytes, 0, $totalBytes, $headerBytes.Length, $fileBytes.Length)
[System.Buffer]::BlockCopy($footerBytes, 0, $totalBytes, $headerBytes.Length + $fileBytes.Length, $footerBytes.Length)

$webRequest = [System.Net.HttpWebRequest]::Create($uri)
$webRequest.Method = "POST"
$webRequest.ContentType = "multipart/form-data; boundary=$boundary"
$webRequest.ContentLength = $totalBytes.Length
$webRequest.Timeout = 120000

$requestStream = $webRequest.GetRequestStream()
$requestStream.Write($totalBytes, 0, $totalBytes.Length)
$requestStream.Close()

$response = $webRequest.GetResponse()
$reader = [System.IO.StreamReader]::new($response.GetResponseStream())
$result = $reader.ReadToEnd()
$reader.Close()
$response.Close()

Write-Host "Upload complete!"
Write-Host $result
