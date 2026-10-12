# ============================================================
#  Auction CQRS - complete API walkthrough (PowerShell)
#  Usage:  powershell -ExecutionPolicy Bypass -File scripts/api-test.ps1
# ============================================================

$ErrorActionPreference = "Stop"
$API = "http://localhost:8080"

$SELLER   = "11111111-1111-1111-1111-111111111111"
$BIDDER_A = "22222222-2222-2222-2222-222222222222"
$BIDDER_B = "33333333-3333-3333-3333-333333333333"

function Step($msg) { Write-Host "`n$msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "  OK  $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "  --  $msg" -ForegroundColor Yellow }

function Post($path, $body) {
    if ($body) {
        curl.exe -s -o NUL -w "%{http_code}" -X POST "$API$path" `
            -H "Content-Type: application/json" -d $body
    } else {
        curl.exe -s -o NUL -w "%{http_code}" -X POST "$API$path"
    }
}

function CreateAuction {
    param($title, $category, $lotQty, $lotUnit, $starting, $increment, $reserve, $buyNow)
    $body = @"
{
  "sellerId": "$SELLER",
  "title": "$title",
  "description": "created by api-test.ps1",
  "category": "$category",
  "lotQuantity": $lotQty,
  "lotUnit": "$lotUnit",
  "startingPrice": "$starting",
  "currency": "BRL",
  "minimumIncrement": "$increment",
  "reservePrice": $(if ($reserve) { "`"$reserve`"" } else { "null" }),
  "buyNowPrice": $(if ($buyNow) { "`"$buyNow`"" } else { "null" }),
  "startsAt": "2020-01-01T00:00:00Z",
  "endsAt": "2030-01-01T00:00:00Z",
  "antiSnipingSeconds": 120
}
"@
    $file = [System.IO.Path]::GetTempFileName()
    Set-Content -Path $file -Value $body -Encoding utf8
    $id = curl.exe -s -X POST "$API/auctions" -H "Content-Type: application/json" -d "@$file"
    Remove-Item $file -Force
    return $id.Trim('"')
}

function PlaceBid {
    param($auctionId, $bidderId, $amount)
    $file = [System.IO.Path]::GetTempFileName()
    $body = "{`"bidderId`":`"$bidderId`",`"amount`":`"$amount`",`"currency`":`"BRL`"}"
    Set-Content -Path $file -Value $body -Encoding utf8
    $resp = curl.exe -s -w "`n%{http_code}" -X POST "$API/auctions/$auctionId/bids" `
        -H "Content-Type: application/json" -d "@$file"
    Remove-Item $file -Force
    return $resp
}

# ------------------------------------------------------------
Step "0. Health check"
# ------------------------------------------------------------
$code = curl.exe -s -o NUL -w "%{http_code}" "$API/queries/auctions/hot"
if ($code -ne "200") { Write-Host "  API not reachable (status $code). Start the app first." -ForegroundColor Red; exit 1 }
Ok "API is up"

# ------------------------------------------------------------
Step "1. POST /auctions - create the auction"
# ------------------------------------------------------------
$auction = CreateAuction -title "Lote de 200 cabecas de gado Nelore" -category "LIVESTOCK" `
             -lotQty 200 -lotUnit "head" -starting "100000.00" -increment "5000.00" `
             -reserve "150000.00" -buyNow $null
Write-Host "  auctionId: $auction"
Ok "created with status DRAFT (aggregateVersion 1)"

# ------------------------------------------------------------
Step "2. GET /auctions/{id} - write side"
# ------------------------------------------------------------
curl.exe -s "$API/auctions/$auction"; Write-Host ""
Ok "status DRAFT, aggregateVersion 1"

# ------------------------------------------------------------
Step "3. POST /auctions/{id}/open"
# ------------------------------------------------------------
Write-Host "  status: $(Post "/auctions/$auction/open")"
Ok "OPEN (aggregateVersion 2)"

# ------------------------------------------------------------
Step "4. POST /auctions/{id}/bids - first bid at starting price"
# ------------------------------------------------------------
Write-Host "  $(PlaceBid $auction $BIDDER_A '100000.00')"
Ok "first bid accepted (aggregateVersion 3)"

# ------------------------------------------------------------
Step "5. NEGATIVE - bid below the minimum increment"
# ------------------------------------------------------------
Write-Host "  $(PlaceBid $auction $BIDDER_B '104999.00')"
Ok "expected: rejected by the domain (needs at least 105000.00)"

# ------------------------------------------------------------
Step "6. NEGATIVE - seller bidding on own auction"
# ------------------------------------------------------------
Write-Host "  $(PlaceBid $auction $SELLER '200000.00')"
Ok "expected: rejected by the domain"

# ------------------------------------------------------------
Step "7. Outbid above the increment"
# ------------------------------------------------------------
Write-Host "  $(PlaceBid $auction $BIDDER_B '160000.00')"
Ok "outbid accepted (aggregateVersion 4)"

# ------------------------------------------------------------
Step "8. POST /auctions/{id}/close"
# ------------------------------------------------------------
Write-Host "  status: $(Post "/auctions/$auction/close")"
Ok "closed as SOLD (reserve 150000 met)"

# ------------------------------------------------------------
Step "9. Waiting for the pipeline (outbox 5s + consumer 3s)"
# ------------------------------------------------------------
Start-Sleep -Seconds 12
Ok "read side should now be consistent"

# ------------------------------------------------------------
Step "10. GET /queries/auctions/{id}/summary - read side"
# ------------------------------------------------------------
curl.exe -s "$API/queries/auctions/$auction/summary"; Write-Host ""
Ok "read model reflects the write side"

# ------------------------------------------------------------
Step "11. GET /queries/auctions/hot?limit=5"
# ------------------------------------------------------------
curl.exe -s "$API/queries/auctions/hot?limit=5"; Write-Host ""
Ok "ranking by bid_count"

# ------------------------------------------------------------
Step "12. GET /queries/auctions/mine?bidderId=..."
# ------------------------------------------------------------
curl.exe -s "$API/queries/auctions/mine?bidderId=$BIDDER_A"; Write-Host ""
curl.exe -s "$API/queries/auctions/mine?bidderId=$BIDDER_B"; Write-Host ""
Ok "each bidder sees the auctions they are winning"

# ------------------------------------------------------------
Step "13. NEGATIVE - bid on a closed auction"
# ------------------------------------------------------------
Write-Host "  $(PlaceBid $auction $BIDDER_A '300000.00')"
Ok "expected: rejected by the domain"

# ------------------------------------------------------------
Step "14. Cancel flow"
# ------------------------------------------------------------
$cancel = CreateAuction -title "Teste de cancelamento" -category "SINGLE_ITEM" `
             -lotQty 1 -lotUnit "unit" -starting "1000.00" -increment "100.00" `
             -reserve $null -buyNow $null
Write-Host "  status: $(Post "/auctions/$cancel/cancel")"
Ok "cancelled"

# ------------------------------------------------------------
Step "15. Buy-now flow - a bid at buyNowPrice closes the auction"
# ------------------------------------------------------------
$buynow = CreateAuction -title "Aeronave com compra imediata" -category "AIRCRAFT" `
             -lotQty 1 -lotUnit "aircraft" -starting "100000.00" -increment "5000.00" `
             -reserve $null -buyNow "150000.00"
Post "/auctions/$buynow/open" | Out-Null
PlaceBid $buynow $BIDDER_A "150000.00" | Out-Null
Start-Sleep -Seconds 12
curl.exe -s "$API/queries/auctions/$buynow/summary"; Write-Host ""
Ok "expected: status SOLD with finalPrice 150000.00"

Write-Host "`nAll steps executed." -ForegroundColor Green
Write-Host "Inspect the database:"
Write-Host "  docker exec -it auction-postgres psql -U auction -d auction"
Write-Host "  \dt"
Write-Host "  SELECT event_type, aggregate_version, published_at IS NOT NULL AS published FROM outbox_event ORDER BY occurred_at;"
Write-Host "  SELECT * FROM auction_summary;"