param([string]$Tag = "v1.61.0")

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
if ($Tag -ne "v1.61.0") { throw "Only the reviewed v1.61.0 source is pinned; review hashes before changing Tag." }

$sources = @(
    @{ Name = "sdk"; Url = "https://raw.githubusercontent.com/googleforgames/agones/v1.61.0/proto/sdk/sdk.proto"; Hash = "F5A4A7A8E9937ED9CF7D992141D3FB90F734AD058999A89B4A66F4AB9DE096E0"; Local = "agones-sdk-java/src/main/proto/agones/sdk.proto" },
    @{ Name = "beta"; Url = "https://raw.githubusercontent.com/googleforgames/agones/v1.61.0/proto/sdk/beta/beta.proto"; Hash = "AB8722D966CA45080659E935F6E42F6C5681D10917EDD3399D0CA260CAA114B4"; Local = "agones-sdk-java/src/main/proto/agones/beta.proto" }
)

$temporary = Join-Path ([System.IO.Path]::GetTempPath()) ("agones-proto-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $temporary | Out-Null
try {
    foreach ($source in $sources) {
        $download = Join-Path $temporary ($source.Name + ".proto")
        Invoke-WebRequest -Uri $source.Url -OutFile $download
        $actual = (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash
        if ($actual -ne $source.Hash) { throw "$($source.Name) source hash changed: $actual" }

        $pattern = '\brpc\s+\w+\s*\([^)]*\)\s+returns\s*\([^)]*\)|\b(?:repeated\s+|map<[^>]+>\s+)?[.\w]+\s+\w+\s*=\s*\d+'
        $upstreamContract = [regex]::Matches((Get-Content -LiteralPath $download -Raw), $pattern) | ForEach-Object { $_.Value -replace '\s+', '' } | Sort-Object -Unique
        $localContract = [regex]::Matches((Get-Content -LiteralPath $source.Local -Raw), $pattern) | ForEach-Object { $_.Value -replace '\s+', '' } | Sort-Object -Unique
        $localOnly = $localContract | Where-Object { $_ -notin $upstreamContract }
        $upstreamOnly = $upstreamContract | Where-Object { $_ -notin $localContract }
        if ($localOnly -or $upstreamOnly) {
            throw "$($source.Name) wire contract differs. Local-only: $($localOnly -join ', '); upstream-only: $($upstreamOnly -join ', ')"
        }
        Write-Host "$($source.Name): pinned hash and vendored declarations verified"
    }
} finally {
    Remove-Item -LiteralPath $temporary -Recurse -Force
}
