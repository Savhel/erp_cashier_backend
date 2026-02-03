# API endpoints used by the frontend

Base prefix: `/api`

Auth: `POST /auth/login` returns a bearer token. All endpoints below require the `Authorization: Bearer <token>` header unless marked **public**.

Timestamps are ISO-8601 strings. Errors are `{ "error": "..." }` with 4xx/5xx unless noted.

## Login flow (RT_ComOps)

All actors use `POST /auth/login` with email + password.

- Body:
  ```json
  { "email": "user@company.com", "password": "..." }
  ```

Success response:
```json
{
  "success": true,
  "user": {
    "id": "...",
    "username": "user@company.com",
    "role": "admin|cashier|user",
    "role_type": null,
    "agency_id": null,
    "organization_id": null
  },
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 0,
  "organizations": [
    {
      "organization_id": "...",
      "organization_name": "...",
      "role_id": "...",
      "role_name": "...",
      "agency_id": "...",
      "agency_name": "...",
      "is_active": true,
      "joined_at": "..."
    }
  ]
}
```

Error response:
```json
{ "error": "Invalid credentials" }
```

## Auth and session

### POST /auth/login
- Body: `{ email, password }`
- 200: login response with bearer token + organizations

### POST /auth/logout
- Body: none
- 200: `{ "success": true }`

### GET /auth/session
- 200:
  ```json
  {
    "user": {
      "id": "...",
      "username": "...",
      "role": "admin|cashier",
      "roleType": "superadmin|organization_admin|agency_admin|null",
      "agencyId": "...|null",
      "organizationId": "...|null"
    },
    "organization": { "id": "...", "name": "..." } | null,
    "agency": { "id": "...", "name": "..." } | null
  }
  ```

## User profile (admin settings)

### GET /users/profile
- 200:
  ```json
  {
    "id": "...",
    "user_name": "...",
    "user_first_name": "...",
    "telegram_chat_id": "...|null",
    "telegram_bot_token": "...|null"
  }
  ```

### PUT /users/profile
- Body:
  ```json
  { "telegram_chat_id": "...|null", "telegram_bot_token": "...|null" }
  ```
- 200:
  ```json
  { "id": "...", "telegram_chat_id": "...|null", "telegram_bot_token": "...|null" }
  ```

## Admin users

### GET /users/admins
- 200: `[{ person + adminProfile + agency + organization }]`

### POST /users/admins
- Body (lookup by phone or person_id):
  ```json
  { "person_id": "..." }
  ```
  or
  ```json
  { "phone": "+237..." }
  ```
- Superadmin also requires `organization_id`.
- Org admin also requires `agency_id`.
- 200: `{ person + adminProfile + agency + organization }`

### PUT /users/admins/[id]
- Body (common profile fields):
  ```json
  {
    "user_first_name": "...",
    "user_name": "...",
    "mail": "...",
    "account_number": "...",
    "country": "...",
    "phone": "...",
    "telegram_chat_id": "...",
    "actif": true
  }
  ```
- Superadmin also uses:
  ```json
  { "organization_id": "...", "organization_bot_token": "..." }
  ```
- Org admin also uses:
  ```json
  { "agency_id": "..." }
  ```
- 200: `{ person + adminProfile + agency + organization }`

### DELETE /users/admins/[id]
- Body: none
- 200: `{ "success": true }`

## Cashiers

### GET /users/cashiers
- 200: `[{ person + cashierProfile + agencyAssignments }]`

### POST /users/cashiers
- Body (required):
  ```json
  {
    "user_name": "...",
    "user_first_name": "...",
    "password": "...",
    "account_number": "...",
    "work_town": "...",
    "base_agency_id": "..."
  }
  ```
- Body (optional):
  ```json
  { "mail": "...", "phone": "...", "country": "...", "town_list_chosen": ["..."], "hire_date": "...", "organization_id": "..." }
  ```
- 200: `{ person + cashierProfile }`

### PUT /users/cashiers/[id]
- Body:
  ```json
  {
    "user_name": "...",
    "user_first_name": "...",
    "country": "...",
    "town_list_chosen": ["..."],
    "work_town": "...",
    "hire_date": "...",
    "organization_id": "...",
    "base_agency_id": "..."
  }
  ```
- 200: `{ person + cashierProfile }`

### DELETE /users/cashiers/[id]
- 200: `{ "success": true }`

## Organizations (ERP admin only)

### GET /organizations
- 200: `[{ id, name, country, description, telegram_bot_token, is_active, creator }]`

### POST /organizations
- Body:
  ```json
  { "name": "...", "country": "...", "description": "...", "telegram_bot_token": "...", "is_active": true }
  ```
- 200: `{ organization }`

### PUT /organizations/[id]
- Body:
  ```json
  { "name": "...", "country": "...", "description": "...", "telegram_bot_token": "...", "is_active": true }
  ```
- 200: `{ organization }`

### DELETE /organizations/[id]
- 200: `{ "success": true }`

## Organizations (RT_ComOps)

### GET /organizations/current
- Proxies RT_ComOps `GET /organizations/current`
- 200: `Organization` (RT_ComOps schema)

### GET /organizations/my
- Proxies RT_ComOps `GET /organizations/my`
- 200: `[Organization]` (RT_ComOps schema)

## Agencies

### GET /agencies
- Proxies RT_ComOps `GET /warehouses`
- Uses `X-Tenant-ID` from the selected organization in the token.
- 200: `[Agency]` (RT_ComOps schema, camelCase)

### GET /agencies/[id]
- Proxies RT_ComOps `GET /warehouses/{id}`
- Uses `X-Tenant-ID` from the selected organization in the token.
- 200: `Agency` (RT_ComOps schema, camelCase)

### POST /agencies
- Proxies RT_ComOps `POST /warehouses`
- Body: RT_ComOps `Agency` schema (camelCase, see `RT_ComOps-openapi.yaml`)
- Do not include `id` or `organizationId` in the body.
- Example body:
  ```json
  {
    "code": "AG-001",
    "name": "Agence-1",
    "shortName": "AG-001",
    "longName": "Premiere agence ACME",
    "location": "Melen Institute",
    "address": "qwerty qwerty",
    "city": "Yaounde",
    "country": "CMR",
    "timezone": "GMT+1",
    "ownerId": null,
    "managerId": null,
    "transferable": false,
    "isHeadquarter": false,
    "isActive": true,
    "isPublic": false,
    "isBusiness": false,
    "isIndividualBusiness": false,
    "logoUri": "https://example.com/logo.png",
    "logoId": null,
    "phone": "650141414",
    "email": "resu@gmail.com",
    "whatsapp": "651201414",
    "socialNetwork": "",
    "website": "",
    "greetingMessage": "Hello",
    "description": "je suis blhack",
    "openTime": "08:00",
    "closeTime": "20:00",
    "averageRevenue": 0,
    "capitalShare": 0,
    "registrationNumber": "qwertyuiasdfghj12454212",
    "taxNumber": "",
    "keywords": [],
    "totalAffiliatedCustomers": 0
  }
  ```
- 200: `Agency` (RT_ComOps schema)

### PATCH /agencies/[id]
- Proxies RT_ComOps `PATCH /warehouses/{id}`
- Body: RT_ComOps `Agency` schema (camelCase)
- Do not include `id` or `organizationId` in the body.
- 200: `Agency` (RT_ComOps schema)

### DELETE /agencies/[id]
- Proxies RT_ComOps `DELETE /warehouses/{id}`
- 200: `{ "success": true }`

## Cash registers

### GET /cash-registers
- 200: `[{ register + agency + assignedCashier + lastSession[] }]`

### POST /cash-registers
- Body:
  ```json
  {
    "adress": "...",
    "country": "...",
    "town": "...",
    "neighborhood": "...",
    "ip_address": "...",
    "mac_address": "...",
    "image_url": "...",
    "min_open_time": "08:00",
    "max_close_time": "20:00"
  }
  ```
- 200: `{ register }`

### GET /cash-registers/[id]
- 200: `{ register + agency + assignedCashier + sessions[] (with movements, ticketing, reconciliation) }`

## Employees (RT_ComOps)

All endpoints below proxy RT_ComOps and require `Authorization: Bearer <token>`.
`X-Tenant-ID` is injected automatically from the selected organization in the token.

### GET /employees/by-email?email=...
- Proxies RT_ComOps `GET /employees/by-email`
- 200: `OrganizationMember` (RT_ComOps schema)

### GET /employees/roles
- Proxies RT_ComOps `GET /employees/roles`
- 200: `[Role]` (RT_ComOps schema)

### POST /employees/invite
- Proxies RT_ComOps `POST /employees/invite`
- Body:
  ```json
  {
    "email": "manager@gmail.com",
    "roleId": "<ROLE_ID>",
    "agencyId": "<AGENCY_ID>"
  }
  ```
- 200: `OrganizationMember` (RT_ComOps schema)

### PUT /cash-registers/[id]
- Body:
  ```json
  {
    "ip_address": "...",
    "mac_address": "...",
    "neighborhood": "...",
    "town": "...",
    "country": "...",
    "is_active": true,
    "min_open_time": "08:00",
    "max_close_time": "20:00"
  }
  ```
- 200: `{ register + agency + assignedCashier + sessions[] }`

### DELETE /cash-registers/[id]
- 200: `{ "success": true }`

### POST /cash-registers/[id]/assign
- Body:
  ```json
  {
    "cashier_id": "...",
    "initial_funds": { "total": 10000, "denominations": { "denom_id": 5 } }
  }
  ```
- 200: `{ assignment }`

## Sessions

### GET /sessions
- 200: `[{ session + cashRegister + movements + ticketingDetails + reconciliation }]`

### POST /sessions
- Body:
  ```json
  { "cash_register_id": "...", "open_by": "...", "theorical_initial_funds": 0 }
  ```
- 200: `{ session }`

### POST /sessions/[id]/close
- Body:
  ```json
  { "physical_total": 0 }
  ```
- 200:
  ```json
  {
    "success": true,
    "message": "Session closed successfully",
    "reconciliation": { "sessionData": { ... }, "reconciliation": { ... } }
  }
  ```

### POST /sessions/[id]/lock
- Body: none
- 200: `{ "success": true, "message": "...", "session": { ... } }`

### DELETE /sessions/[id]/lock
- Body: none
- 200: `{ "success": true, "message": "...", "session": { ... } }`

### GET /cashier/sessions
- 200: `[{ session + cashRegister + movements + ticketingDetails + reconciliation }]`

## Assignments

### GET /admin/assignments
- 200: `[{ id, person, cashRegister, day, ... }]`

### GET /admin/cashier-agency-assignments
- 200: `[{ id, cashier, agency, start_on, end_on, assigned_on }]`

### POST /admin/cashier-agency-assignments
- Body:
  ```json
  { "cashier_id": "...", "agency_id": "...", "start_on": "YYYY-MM-DD", "end_on": "YYYY-MM-DD" }
  ```
- 200: `{ assignment }`

### DELETE /admin/cashier-agency-assignments
- Body:
  ```json
  { "id": "ASSIGNMENT_ID" }
  ```
- 200: `{ assignment }` (updated end_on)

## Accounts and customers

### GET /admin/accounts
- 200:
  ```json
  [
    {
      "id": "...",
      "account_number": "...",
      "total_funds": 0,
      "is_active": true,
      "create_on": "...",
      "ownerId": "...",
      "owner": { "name": "...", "username": "...", "role": "customer|cashier|admin" },
      "events": [ ... ],
      "operations": [ ... ]
    }
  ]
  ```

### GET /admin/customers
- 200: `[{ id, person, phone, accounts, totalBalance, accountsCount }]`

### POST /admin/customers
- Body:
  ```json
  {
    "phone": "+237...",
    "user_first_name": "...",
    "user_name": "...",
    "mail": "...",
    "country": "...",
    "profession": "...",
    "account_number": "...",
    "initial_balance": 0
  }
  ```
- 200: `{ id, person, accounts: [account] }`

### GET /cashier/accounts
- 200: `[{ account + customer + events }]`

### GET /cashier/customers
- 200: `[{ customer + person + accounts + totalBalance + accountsCount }]`

### GET /customers/search
- Query: `q`
- 200: `[{ customer + person + accounts }]`

### POST /accounts/transfer (deposit)
- Body:
  ```json
  { "account_id": "...", "amount": 1000, "ticketing": [], "reason": "...", "reference": "..." }
  ```
- 200: `{ "success": true, "newBalance": 0, "movementId": "...", "reference": "..." }`

### POST /accounts/withdraw
- Body:
  ```json
  { "account_id": "...", "amount": 1000, "ticketing": [], "reason": "...", "reference": "..." }
  ```
- 200: `{ "success": true, "newBalance": 0, "movementId": "...", "reference": "..." }`

### POST /accounts/transfer-p2p
- Body:
  ```json
  { "source_account_id": "...", "dest_account_id": "...", "amount": 1000, "ticketing": [], "reference": "..." }
  ```
- 200: `{ "success": true, "inMovementId": "...", "outMovementId": "...", "reference": "..." }`

## Bills

### GET /cashier/bills
- 200: `[{ id, invoice_code, amount, customer_name, due_date, cash_register_id, payment_mode, items[], account? }]`

### GET /cashier/bills/[id]
- 200: `{ bill }` (same shape as list item)

### GET /bills
- Query: `page?`, `limit?`, `search?`
- 200: `{ bills: [movement], total, page, totalPages }`

### POST /bills/pay
- Body:
  ```json
  {
    "invoice_code": "...",
    "amount": 1000,
    "payment_mode": "cash|account",
    "cash_given": 1000,
    "ticketing": [],
    "change_ticketing": [],
    "account_id": "..."
  }
  ```
- 200 (cash): `{ "success": true, "movement_id": "...", "change": 0 }`
- 200 (account): `{ "success": true, "inMovementId": "...", "outMovementId": "...", "reference": "..." }`

## Movements and transactions

### GET /cashier/movements
- Query: `sense?`, `hasInvoice?`, `isTransfer?`, `type?`
- `type` values: `deposit`, `withdrawal`, `p2p_transfer`, `bill`, `change`
- 200: `[{ movement + recipient?, emitter?, sourceRegister?, destinationRegister? }]`

### POST /movements/transfer
- Body:
  ```json
  { "amount": 1000, "ticketing": [] }
  ```
- 200: `{ "success": true, "message": "...", "newBalance": 0, "sourceRegister": { ... } }`

### POST /movements/[id]/account
- Body: none
- 200: `{ "success": true, "movement": { ... } }`

### GET /transactions
- Query: `startDate?`, `endDate?`, `registerId?`, `cashierId?`, `type?`, `page?`, `limit?`
- 200: `{ movements: [movement], total, page, totalPages }`

### GET /transactions/recent
- Requires `Authorization: Bearer <token>` and `X-Tenant-ID: <ORG_ID>`
- 200 (array, max 5):
  ```json
  [
    {
      "id": "uuid",
      "amount": 25000,
      "sense": "entree",
      "reason": "Deposit",
      "createdAt": "2026-01-31T08:12:34.000Z",
      "cashier": "Jean",
      "register": "Douala",
      "customer": null,
      "externalReference": "EXT-123"
    }
  ]
  ```

## Reconciliations

### GET /admin/reconciliations
- 200: `[{ reconciliation + session + cashRegister + opener + closer + creator }]`

### GET /cashier/reconciliations
- 200: `[{ reconciliation + session + cashRegister + opener + closer + creator }]`

### POST /reconciliations/[id]/review
- Body:
  ```json
  { "action": "valide" | "rejete", "admin_comment": "..." }
  ```
- 200: `{ "success": true, "message": "...", "reconciliation": { ... } }`

### POST /reconciliations/[id]/justify
- Body:
  ```json
  { "justification": "..." }
  ```
- 200: `{ "success": true, "message": "...", "reconciliation": { ... } }`

## Reports and dashboard

### GET /reports/transactions
- Query: `startDate?`, `endDate?`, `registerId?`, `cashierId?`, `type?`
- 200: PDF (`Content-Type: application/pdf`)

### POST /reports/register/[id]
- Body:
  ```json
  { "startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD" }
  ```
- 200: PDF (`Content-Type: application/pdf`)

### GET /reports/session/[id]
- 200: PDF (`Content-Type: application/pdf`)

### GET /dashboard/stat
- Requires `Authorization: Bearer <token>` and `X-Tenant-ID: <ORG_ID>`
- 200:
  ```json
  {
    "role": "admin",
    "totalRevenue": 1234567,
    "activeSessions": 4,
    "todayMovements": 38,
    "todayTotal": 125000,
    "monthlyRevenue": [
      { "name": "Jan", "total": 12.34 },
      { "name": "Feb", "total": 5.67 }
    ],
    "dailyRevenue": [
      { "name": "lun. 29 jan.", "total": 1.2 },
      { "name": "mar. 30 jan.", "total": 0.8 }
    ],
    "hourlyRevenue": [
      { "name": "08", "total": 0.12 },
      { "name": "09", "total": -0.05 }
    ]
  }
  ```

## Documents, audit, notifications

### GET /admin/documents
- 200: `[{ document + uploader + adminProfile }]`

### GET /audit
- Query: `limit?`, `agencyId?`
- 200: `[ { id, type, date_time, payload, author } ]`

### POST /audit
- Body:
  ```json
  { "path": "...", "method": "...", "ip": "...", "payload": { ... } }
  ```
- 200: `{ "success": true }`

### POST /notify-unauthorized
- Body:
  ```json
  { "path": "...", "username": "...", "userId": "...", "agencyId": "...", "organizationId": "...", "ip": "...", "userAgent": "...", "macAddress": "..." }
  ```
- 200: `{ "ok": true }`

### GET /notifications
- 200:
  ```json
  { "newsletters": [ { "id": "...", "title": "...", "content": "..." } ], "forums": [ { "id": "...", "title": "...", "messages": [ ... ] } ] }
  ```

### POST /notifications/test
- Body:
  ```json
  { "chat_id": "...", "bot_token": "..." }
  ```
- 200: `{ "ok": true }`

## Config

### GET /config/denominations
- 200: `[{ id, currency, value, label, order, is_active }]`

## Public (no auth)

### GET /public/organizations
- 200: `[{ id, name, country, is_active }]`

### GET /public/agencies
- Query: `organizationId`
- 200: `[{ id, name, country, town, neighborhood, is_active }]`

## External or upstream platform endpoints (mocked today)

These are expected to be provided by another system and are mocked locally for now.

### GET /lookup/admin
- Query: `phone`
- 200: `{ person + adminProfile }`

### GET /lookup/cashier
- Query: `id`
- 200: `{ id, user_name, user_first_name, account_number, country, mail?, phone?, password?, source }`

### GET /lookup/customer
- Query: `phone`
- 200: `{ phone, user_name, user_first_name, mail, country, profession, source }`

### GET /lookup/organization
- Query: `code`
- 200: `{ id, name, country?, description?, telegram_bot_token?, is_active? }`
