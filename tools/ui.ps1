param (
    [Parameter(Position=0)]
    [string]$Command = "status",

    [Parameter(Position=1)]
    [string]$Arg1 = "",

    [Parameter(Position=2)]
    [string]$Arg2 = ""
)

$ErrorActionPreference = "Continue"
$DeviceSerial = "R5CRC0LJX7K"
$Port = 8765

function Ensure-Forward {
    adb -s $DeviceSerial forward tcp:$Port tcp:$Port 2>$null
}

function Get-AppStatus {
    Ensure-Forward
    try {
        $res = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/status" -TimeoutSec 3
        Write-Host "=== ArchiveTune State ===" -ForegroundColor Cyan
        Write-Host "Route:    $($res.currentRoute)" -ForegroundColor Yellow
        Write-Host "Spotify:  Connected=$($res.spotify.isConnected), User=$($res.spotify.username), ValidSession=$($res.spotify.sessionValid)" -ForegroundColor Green
        Write-Host "Player:   Playing=$($res.player.isPlaying), State=$($res.player.playbackState)" -ForegroundColor Magenta
        if ($res.player.currentTrack) {
            Write-Host "Track:    $($res.player.currentTrack.title) - $($res.player.currentTrack.artist) ($($res.player.currentTrack.album))" -ForegroundColor White
            $posSec = [math]::Round($res.player.positionMs / 1000)
            $durSec = [math]::Round($res.player.durationMs / 1000)
            Write-Host "Progress: $posSec s / $durSec s" -ForegroundColor Gray
        }
        if ($res.player.hasError) {
            Write-Host "Error:    $($res.player.error)" -ForegroundColor Red
        }
        return $res
    } catch {
        Write-Host "[!] HTTP bridge unreachable on port ${Port}: $_" -ForegroundColor DarkYellow
        Write-Host "[*] Falling back to uiautomator dump..." -ForegroundColor Gray
        Dump-UI
    }
}

function Navigate-Route([string]$route) {
    Ensure-Forward
    try {
        $encoded = [System.Uri]::EscapeDataString($route)
        $res = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/nav?route=$encoded" -TimeoutSec 3
        Write-Host "Navigated to: $($res.navigatedTo)" -ForegroundColor Green
    } catch {
        Write-Host "[*] Using adb intent to navigate to $route..." -ForegroundColor Gray
        adb -s $DeviceSerial shell "am start -n moe.rukamori.archivetune.debug/moe.rukamori.archivetune.MainActivity --es navigate_to '$route'"
    }
}

function Send-Action([string]$action, [string]$extra = "") {
    Ensure-Forward
    try {
        $url = "http://127.0.0.1:$Port/action?name=$action"
        if ($extra) { $url += "&$extra" }
        $res = Invoke-RestMethod -Uri $url -TimeoutSec 3
        Write-Host "Action ${action}: $($res.status)" -ForegroundColor Green
    } catch {
        Write-Host "[!] HTTP action failed, sending broadcast..." -ForegroundColor Gray
        adb -s $DeviceSerial shell "am broadcast -a moe.rukamori.archivetune.DEBUG --es action '$action'"
    }
}

function Dump-UI {
    adb -s $DeviceSerial shell "uiautomator dump /sdcard/dump.xml >/dev/null && cat /sdcard/dump.xml" > "$PSScriptRoot\..\dump.xml"
    if (-not (Test-Path "$PSScriptRoot\..\dump.xml")) {
        Write-Host "[!] Failed to retrieve dump.xml" -ForegroundColor Red
        return
    }

    [xml]$xml = Get-Content "$PSScriptRoot\..\dump.xml" -Raw
    Write-Host "`n=== Screen UI Elements ===" -ForegroundColor Cyan
    $nodes = $xml.SelectNodes("//node[@text != '' or @content-desc != '']")
    foreach ($node in $nodes) {
        $text = if ($node.text) { $node.text } else { $node.'content-desc' }
        $bounds = $node.bounds
        $clickable = $node.clickable

        # Parse bounds [x1,y1][x2,y2]
        if ($bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
            $x1 = [int]$matches[1]; $y1 = [int]$matches[2]
            $x2 = [int]$matches[3]; $y2 = [int]$matches[4]
            $cx = [int](($x1 + $x2) / 2)
            $cy = [int](($y1 + $y2) / 2)
            $typeStr = if ($clickable -eq "true") { "[BTN]" } else { "[TXT]" }
            $color = if ($clickable -eq "true") { "Yellow" } else { "White" }
            Write-Host ("{0,-6} {1,-35} Center: ({2,4},{3,4}) Bounds: {4}" -f $typeStr, $text, $cx, $cy, $bounds) -ForegroundColor $color
        }
    }
}

function Tap-Text([string]$targetText) {
    adb -s $DeviceSerial shell "uiautomator dump /sdcard/dump.xml >/dev/null && cat /sdcard/dump.xml" > "$PSScriptRoot\..\dump.xml"
    [xml]$xml = Get-Content "$PSScriptRoot\..\dump.xml" -Raw
    $node = $xml.SelectSingleNode("//node[contains(@text, '$targetText') or contains(@content-desc, '$targetText')]")
    if ($null -eq $node) {
        Write-Host "[!] Text '$targetText' not found on screen." -ForegroundColor Red
        return
    }

    $bounds = $node.bounds
    if ($bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        $cx = [int](([int]$matches[1] + [int]$matches[3]) / 2)
        $cy = [int](([int]$matches[2] + [int]$matches[4]) / 2)
        Write-Host "[*] Tapping '$targetText' at ($cx, $cy)..." -ForegroundColor Green
        adb -s $DeviceSerial shell "input tap $cx $cy"
    }
}

function Search-Spotify([string]$query) {
    Ensure-Forward
    $encoded = [System.Uri]::EscapeDataString($query)
    $res = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/spotify_search?q=$encoded" -TimeoutSec 15
    Write-Host "=== Spotify Search Results ($($res.count)) ===" -ForegroundColor Green
    foreach ($t in $res.tracks) {
        Write-Host "• $($t.name) - $($t.artists) ($($t.album)) [id: $($t.id)]" -ForegroundColor White
    }
    return $res
}

function Get-SpotifyPlaylists {
    Ensure-Forward
    $res = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/spotify_playlists" -TimeoutSec 15
    Write-Host "=== User Spotify Playlists ($($res.count)) ===" -ForegroundColor Green
    foreach ($p in $res.playlists) {
        Write-Host "• $($p.name) (Tracks: $($p.itemCount), CanEdit: $($p.canEdit)) [id: $($p.id)]" -ForegroundColor White
    }
    return $res
}

function Add-SpotifyTrack([string]$trackUri, [string]$playlistId) {
    Ensure-Forward
    $uriEncoded = [System.Uri]::EscapeDataString($trackUri)
    $url = "http://127.0.0.1:$Port/spotify_add?trackUri=$uriEncoded"
    if ($playlistId) { $url += "&playlistId=$playlistId" }
    $res = Invoke-RestMethod -Uri $url -TimeoutSec 15
    Write-Host "Add to Spotify: $($res.status) (Target: $($res.target))" -ForegroundColor Green
    return $res
}

function Play-SpotifySearch([string]$query, [int]$idx = 0) {
    Ensure-Forward
    $encoded = [System.Uri]::EscapeDataString($query)
    $res = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/play_spotify_search?q=$encoded&index=$idx" -TimeoutSec 15
    Write-Host "Play Spotify Search: $($res.status) -> $($res.playing)" -ForegroundColor Green
    return $res
}

switch ($Command.ToLower()) {
    "status"            { Get-AppStatus }
    "state"             { Get-AppStatus }
    "dump"              { Dump-UI }
    "nav"               { Navigate-Route $Arg1 }
    "tap"               { Tap-Text $Arg1 }
    "play"              { Send-Action "play" }
    "pause"             { Send-Action "pause" }
    "next"              { Send-Action "next" }
    "prev"              { Send-Action "prev" }
    "reconnect"         { Send-Action "reconnect_spotify" }
    "oauth"             { Send-Action "oauth_login" }
    "set_token"         { Send-Action "set_token" "token=$Arg1" }
    "search-spotify"    { Search-Spotify $Arg1 }
    "playlists-spotify" { Get-SpotifyPlaylists }
    "add-spotify"       { Add-SpotifyTrack $Arg1 $Arg2 }
    "play-spotify"      { Play-SpotifySearch $Arg1 $Arg2 }
    "play-yt"           { Send-Action "play_youtube_id" "id=$Arg1" }
    "back"              { adb -s $DeviceSerial shell "input keyevent 4" }
    "wake"              { adb -s $DeviceSerial shell "input keyevent 26 && input keyevent 82" }
    default             {
        Write-Host "Usage: .\tools\ui.ps1 <status|dump|nav <r>|tap <t>|search-spotify <q>|playlists-spotify|add-spotify <uri> [pid]|play-spotify <q>|play|pause|next|prev|oauth|set_token <t>|back|wake>"
    }
}

