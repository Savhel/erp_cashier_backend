#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
TOKEN="${TOKEN:-}"
ORG_ID="${ORG_ID:-}"

if [[ -z "$TOKEN" || -z "$ORG_ID" ]]; then
  echo "Usage: TOKEN=... ORG_ID=... ./create_warehouse.sh" >&2
  echo "Optional env: CODE, NAME, SHORT_NAME, LONG_NAME, LOCATION, ADDRESS, CITY, COUNTRY, TIMEZONE," >&2
  echo "  OWNER_ID, MANAGER_ID, TRANSFERABLE, IS_HEADQUARTER, IS_ACTIVE, IS_PUBLIC, IS_BUSINESS," >&2
  echo "  IS_INDIVIDUAL_BUSINESS, LOGO_URI, LOGO_ID, PHONE, EMAIL, WHATSAPP, SOCIAL_NETWORK," >&2
  echo "  WEBSITE, GREETING_MESSAGE, DESCRIPTION, OPEN_TIME, CLOSE_TIME, AVERAGE_REVENUE," >&2
  echo "  CAPITAL_SHARE, REGISTRATION_NUMBER, TAX_NUMBER, KEYWORDS, TOTAL_AFFILIATED_CUSTOMERS" >&2
  exit 1
fi

CODE="${CODE:-AG-001}"
NAME="${NAME:-Agence-1}"
SHORT_NAME="${SHORT_NAME:-AG-001}"
LONG_NAME="${LONG_NAME:-Premiere agence ACME}"
LOCATION="${LOCATION:-Melen Institute}"
ADDRESS="${ADDRESS:-qwerty qwerty}"
CITY="${CITY:-Yaounde}"
COUNTRY="${COUNTRY:-CMR}"
TIMEZONE="${TIMEZONE:-GMT+1}"

OWNER_ID="${OWNER_ID:-}"
MANAGER_ID="${MANAGER_ID:-}"
LOGO_ID="${LOGO_ID:-}"

TRANSFERABLE="${TRANSFERABLE:-false}"
IS_HEADQUARTER="${IS_HEADQUARTER:-false}"
IS_ACTIVE="${IS_ACTIVE:-false}"
IS_PUBLIC="${IS_PUBLIC:-false}"
IS_BUSINESS="${IS_BUSINESS:-false}"
IS_INDIVIDUAL_BUSINESS="${IS_INDIVIDUAL_BUSINESS:-false}"

LOGO_URI="${LOGO_URI:-https://example.com/logo.png}"
PHONE="${PHONE:-650141414}"
EMAIL="${EMAIL:-resu@gmail.com}"
WHATSAPP="${WHATSAPP:-651201414}"
SOCIAL_NETWORK="${SOCIAL_NETWORK:-}"
WEBSITE="${WEBSITE:-}"
GREETING_MESSAGE="${GREETING_MESSAGE:-Hello}"
DESCRIPTION="${DESCRIPTION:-je suis blhack}"
OPEN_TIME="${OPEN_TIME:-08:00}"
CLOSE_TIME="${CLOSE_TIME:-20:00}"

AVERAGE_REVENUE="${AVERAGE_REVENUE:-0}"
CAPITAL_SHARE="${CAPITAL_SHARE:-0}"
REGISTRATION_NUMBER="${REGISTRATION_NUMBER:-qwertyuiasdfghj12454212}"
TAX_NUMBER="${TAX_NUMBER:-}"
KEYWORDS_JSON="${KEYWORDS_JSON:-[]}"
TOTAL_AFFILIATED_CUSTOMERS="${TOTAL_AFFILIATED_CUSTOMERS:-0}"

owner_json="null"
manager_json="null"
logo_id_json="null"

if [[ -n "$OWNER_ID" ]]; then
  owner_json="\"$OWNER_ID\""
fi

if [[ -n "$MANAGER_ID" ]]; then
  manager_json="\"$MANAGER_ID\""
fi

if [[ -n "$LOGO_ID" ]]; then
  logo_id_json="\"$LOGO_ID\""
fi

payload=$(cat <<EOF
{
  "code": "$CODE",
  "name": "$NAME",
  "shortName": "$SHORT_NAME",
  "longName": "$LONG_NAME",
  "location": "$LOCATION",
  "address": "$ADDRESS",
  "city": "$CITY",
  "country": "$COUNTRY",
  "timezone": "$TIMEZONE",
  "ownerId": $owner_json,
  "managerId": $manager_json,
  "transferable": $TRANSFERABLE,
  "isHeadquarter": $IS_HEADQUARTER,
  "isActive": $IS_ACTIVE,
  "isPublic": $IS_PUBLIC,
  "isBusiness": $IS_BUSINESS,
  "isIndividualBusiness": $IS_INDIVIDUAL_BUSINESS,
  "logoUri": "$LOGO_URI",
  "logoId": $logo_id_json,
  "phone": "$PHONE",
  "email": "$EMAIL",
  "whatsapp": "$WHATSAPP",
  "socialNetwork": "$SOCIAL_NETWORK",
  "website": "$WEBSITE",
  "greetingMessage": "$GREETING_MESSAGE",
  "description": "$DESCRIPTION",
  "openTime": "$OPEN_TIME",
  "closeTime": "$CLOSE_TIME",
  "averageRevenue": $AVERAGE_REVENUE,
  "capitalShare": $CAPITAL_SHARE,
  "registrationNumber": "$REGISTRATION_NUMBER",
  "taxNumber": "$TAX_NUMBER",
  "keywords": $KEYWORDS_JSON,
  "totalAffiliatedCustomers": $TOTAL_AFFILIATED_CUSTOMERS
}
EOF
)

curl -i -X POST "$API_BASE_URL/warehouses" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: $ORG_ID" \
  -H "Content-Type: application/json" \
  -d "$payload"
