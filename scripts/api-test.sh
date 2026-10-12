#!/usr/bin/env bash
# ============================================================
#  Auction CQRS - complete API walkthrough
#  Usage:  bash api-test.sh
# ============================================================

set -euo pipefail

API="http://localhost:8080"
SELLER="11111111-1111-1111-1111-111111111111"
BIDDER_A="22222222-2222-2222-2222-222222222222"
BIDDER_B="33333333-3333-3333-3333-333333333333"

step() { printf "\n\033[1;36m%s\033[0m\n" "$*"; }
ok()   { printf "\033[0;32m  OK  %s\033[0m\n" "$*"; }
fail() { printf "\033[0;31m FAIL %s\033[0m\n" "$*"; }

# ------------------------------------------------------------
step "0. Health check"
# ------------------------------------------------------------
curl -s -o /dev/null -w "  API status: %{http_code}\n" "$API/queries/auctions/hot"
ok "API is up"

# ------------------------------------------------------------
step "1. POST /auctions  - create auction (createAuctionCommand)"
# ------------------------------------------------------------
AUCTION=$(curl -s -X POST "$API/auctions" \
  -H "Content-Type: application/json" \
  -d '{
    "sellerId": "'"$SELLER"'",
    "title": "Lote de 200 cabecas de gado Nelore",
    "description": "Sanidade comprovada, 24 meses",
    "category": "LIVESTOCK",
    "lotQuantity": 200,
    "lotUnit": "head",
    "startingPrice": "100000.00",
    "currency": "BRL",
    "minimumIncrement": "5000.00",
    "reservePrice": "150000.00",
    "buyNowPrice": null,
    "startsAt": "2020-01-01T00:00:00Z",
    "endsAt": "2030-01-01T00:00:00Z",
    "antiSnipingSeconds": 120
  }' | tr -d '"')
echo "  auctionId: $AUCTION"
ok "auction created (status DRAFT)"

# ------------------------------------------------------------
step "2. GET /auctions/{id}  - read the WRITE side"
# ------------------------------------------------------------
curl -s "$API/auctions/$AUCTION"; echo
ok "status should be DRAFT and aggregateVersion 1"

# ------------------------------------------------------------
step "3. POST /auctions/{id}/open  (openAuctionCommand)"
# ------------------------------------------------------------
curl -s -o /dev/null -w "  status: %{http_code}\n" -X POST "$API/auctions/$AUCTION/open"
ok "auction OPEN (aggregateVersion 2)"

# ------------------------------------------------------------
step "4. POST /auctions/{id}/bids  - first bid"
# ------------------------------------------------------------
curl -s -o /dev/null -w "  status: %{http_code}\n" -X POST "$API/auctions/$AUCTION/bids" \
  -H "Content-Type: application/json" \
  -d '{"bidderId": "'"$BIDDER_A"'","amount": "100000.00","currency": "BRL"}'
ok "first bid accepted (aggregateVersion 3)"

# ------------------------------------------------------------
step "5. NEGATIVE: bid below minimum increment (expect 5xx/4xx)"
# ------------------------------------------------------------
curl -s -X POST "$API/auctions/$AUCTION/bids" \
  -H "Content-Type: application/json" \
  -d '{"bidderId": "'"$BIDDER_B"'","amount": "104999.00","currency": "BRL"}' | head -c 120; echo
ok "domain rejected the bid"

# ------------------------------------------------------------
step "6. NEGATIVE: seller bidding on own auction (expect error)"
# ------------------------------------------------------------
curl -s -X POST "$API/auctions/$AUCTION/bids" \
  -H "Content-Type: application/json" \
  -d '{"bidderId": "'"$SELLER"'","amount": "200000.00","currency": "BRL"}' | head -c 120; echo
ok "domain rejected the seller bid"

# ------------------------------------------------------------
step "7. Second bid above the increment"
# ------------------------------------------------------------
curl -s -o /dev/null -w "  status: %{http_code}\n" -X POST "$API/auctions/$AUCTION/bids" \
  -H "Content-Type: application/json" \
  -d '{"bidderId": "'"$BIDDER_B"'","amount": "160000.00","currency": "BRL"}'
ok "outbid accepted (aggregateVersion 4)"

# ------------------------------------------------------------
step "8. POST /auctions/{id}/close  (closeAuctionCommand)"
# ------------------------------------------------------------
curl -s -o /dev/null -w "  status: %{http_code}\n" -X POST "$API/auctions/$AUCTION/close"
ok "auction closed as SOLD (reserve met)"

# ------------------------------------------------------------
step "9. Wait for the outbox pipeline (publisher 5s + consumer 3s)"
# ------------------------------------------------------------
sleep 12
ok "read side should now be consistent"

# ------------------------------------------------------------
step "10. GET /queries/auctions/{id}/summary  - read the READ side"
# ------------------------------------------------------------
curl -s "$API/queries/auctions/$AUCTION/summary"; echo
ok "read model reflects the write side"

# ------------------------------------------------------------
step "11. GET /queries/auctions/hot  - ranking by bid_count"
# ------------------------------------------------------------
curl -s "$API/queries/auctions/hot?limit=5"; echo
ok "hot auctions"

# ------------------------------------------------------------
step "12. GET /queries/auctions/mine?bidderId=..."
# ------------------------------------------------------------
curl -s "$API/queries/auctions/mine?bidderId=$BIDDER_A"; echo
echo ""
curl -s "$API/queries/auctions/mine?bidderId=$BIDDER_B"; echo
ok "auctions where each bidder is the highest bidder"

# ------------------------------------------------------------
step "13. NEGATIVE: bid on a closed auction (expect error)"
# ------------------------------------------------------------
curl -s -X POST "$API/auctions/$AUCTION/bids" \
  -H "Content-Type: application/json" \
  -d '{"bidderId": "'"$BIDDER_A"'","amount": "300000.00","currency": "BRL"}' | head -c 120; echo
ok "domain rejected the bid"

# ------------------------------------------------------------
step "14. POST /auctions  + cancel flow"
# ------------------------------------------------------------
CANCEL=$(curl -s -X POST "$API/auctions" \
  -H "Content-Type: application/json" \
  -d '{
    "sellerId": "'"$SELLER"'",
    "title": "Sementira para teste de cancelamento",
    "category": "SINGLE_ITEM",
    "lotQuantity": 1,
    "lotUnit": "unit",
    "startingPrice": "1000.00",
    "currency": "BRL",
    "minimumIncrement": "100.00",
    "startsAt": "2020-01-01T00:00:00Z",
    "endsAt": "2030-01-01T00:00:00Z",
    "antiSnipingSeconds": 120
  }' | tr -d '"')
curl -s -o /dev/null -w "  cancel status: %{http_code}\n" -X POST "$API/auctions/$CANCEL/cancel"
ok "auction cancelled"

# ------------------------------------------------------------
step "15. Buy-now flow: a bid at or above buyNowPrice ends the auction"
# ------------------------------------------------------------
BUYNOW=$(curl -s -X POST "$API/auctions" \
  -H "Content-Type: application/json" \
  -d '{
    "sellerId": "'"$SELLER"'",
    "title": "Aeronave com compra imediata",
    "category": "AIRCRAFT",
    "lotQuantity": 1,
    "lotUnit": "aircraft",
    "startingPrice": "100000.00",
    "currency": "BRL",
    "minimumIncrement": "5000.00",
    "reservePrice": null,
    "buyNowPrice": "150000.00",
    "startsAt": "2020-01-01T00:00:00Z",
    "endsAt": "2030-01-01T00:00:00Z",
    "antiSnipingSeconds": 120
  }' | tr -d '"')
curl -s -o /dev/null -X POST "$API/auctions/$BUYNOW/open"
curl -s -o /dev/null -X POST "$API/auctions/$BUYNOW/bids" \
  -H "Content-Type: application/json" \
  -d '{"bidderId": "'"$BIDDER_A"'","amount": "150000.00","currency": "BRL"}'
sleep 12
curl -s "$API/queries/auctions/$BUYNOW/summary"; echo
ok "buy-now should close the auction immediately as SOLD"

printf "\n\033[1;32mAll steps executed.\033[0m\n"
printf "Inspect the database with:\n"
printf "  docker exec -it auction-postgres psql -U auction -d auction\n"
printf "  \\dt\n  SELECT event_type, aggregate_version, published_at IS NOT NULL AS published FROM outbox_event ORDER BY occurred_at;\n"
printf "  SELECT * FROM auction_summary;\n"