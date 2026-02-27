# Core vs Cashier Schema Split (target)

This document defines which schemas from this project must be owned by the **Core** module (RH + Material + Accounts), and which stay in the **Cashier** module.

## 1) Ownership rule

- **Core owns master data and account ledger truth**.
- **Cashier owns session execution and operational cash workflow**.
- Cashier can keep local **read projections/cache** from Core, but not business truth for Core-owned entities.

## 2) Schemas from this project that must be in Core

These current schemas/tables should be Core-owned:

1. `person`
2. `admin_profile`
3. `cashier_profile`
4. `customer_profile`
5. `organization`
6. `agency`
7. `cash_register`
8. `cashier_agency_assignment`
9. `cashier_manage_cash_register` (or equivalent assignment history)
10. `account`
11. `currency_denomination`
12. `attached_document` (for customer/account/person/agency/org content)
13. `bill`, `bill_item` (if billing is not a separate product)

## 3) Core schema definitions (from current project models)

### 3.1 Identity / RH

#### `person`
- `id`, `user_name`, `user_first_name`, `password`, `actif`
- `phone`, `mail`, `country`, `account_number`
- `telegram_chat_id`, `telegram_bot_token`

#### `admin_profile`
- `id`, `person_id`, `role_type`
- `agency_id`, `organization_id`
- monitoring flags (`monitor_all_agencies`, `monitor_agency_ids`, `monitor_all_registers`, `monitor_register_ids`)

#### `cashier_profile`
- `id`, `person_id`
- `organization_id`, `base_agency_id`
- `town_list_chosen`, `work_town`, `hire_date`

#### `customer_profile`
- `id`, `person_id`
- `profession`, `date_of_joining`

### 3.2 Organization / Material

#### `organization`
- `id`, `name`, `country`, `description`, `is_active`
- `create_on`, `create_by`, `telegram_bot_token`

#### `agency`
- `id`, `organization_id`, `name`
- `country`, `town`, `neighborhood`, `address`, `location_hint`
- `is_active`, `requires_admin_assignment`, `create_on`

#### `cash_register`
- `id`, `agency_id`, `user_id` (assigned cashier if needed)
- `is_active`, `double_closing_count`, `justifiable_threshold`
- `country`, `town`, `neighborhood`, `adress`
- `ip_address`, `mac_address`, `image_url`
- `sale_agent_bank_account`, `sale_agent_accounting_account`

#### `cashier_agency_assignment`
- `id`, `cashier_id`, `agency_id`
- `start_on`, `end_on`, `assigned_on`, `assigned_by`

#### `cashier_manage_cash_register`
- `id`, `cash_register_id`, `user_id`, `day`

### 3.3 Accounts / Commercial content

#### `account`
- `id`, `client_id`
- `account_number`, `accounting_account`
- `is_active`, `create_on`, `create_by`
- `total_funds`, `previous_event_hash`

#### `currency_denomination`
- `id`, `currency`, `value`, `label`, `order`, `is_active`

#### `attached_document`
- `id`, `objet_type`, `objet_id`
- `file_name`, `type_mime`, `storage_url`
- `upload_on`, `upload_by`, `is_verified`, `is_deleted`

#### `bill` / `bill_item` (if in Core scope)
- `bill`: `id`, `organization_id`, `invoice_code`, `amount`, `customer_name`, `due_date`, `payment_mode`, `account_id`, `create_on`, `is_deleted`
- `bill_item`: `id`, `bill_id`, `description`, `quantity`, `amount`

## 4) Schemas that stay in Cashier

These remain operational in Cashier:

1. `cash_register_session`
2. `cash_register_movement` (operational mirror; references Core account IDs)
3. `event_ticketing_detail`
4. `cash_reconciliation`
5. `cash_register_event` (audit/event log for cashier operations)
6. `rt_sync_state` and other technical sync tables

Important:
- `cash_register_movement.emitter_id` and `recipient_id` must carry **Core `account.id`** values.
- Final debit/credit truth remains in Core ledger; cashier movement is operational trace.

## 5) Session + operations contract with Core

- Agency admin opens session (assign register + cashier), with Core authorization.
- Cashier can work only if one active session exists for him.
- Deposit/withdraw during session:
  - validate session in Cashier,
  - execute account transfer in Core (idempotent),
  - persist local cashier movement/audit/ticketing.

## 6) `X-Tenant-ID` vs `X-Correlation-Id`

They are not the same purpose. Keep both:

- `X-Tenant-ID`: tenant routing/scope (`organization_id`).
- `X-Correlation-Id`: traceability across services/logs for one request flow.

Recommendation:
- Require `X-Correlation-Id` always.
- Keep `X-Tenant-ID` if your platform already uses it.
- Validate that `X-Tenant-ID` matches tenant from JWT/service token claims.

## 7) Migration plan (table by table)

Goal: move Core-owned truth out of Cashier without breaking operations.

### Phase 0 - Foundation (no data move yet)

1. Add Core client in Cashier (service-to-service auth).
2. Enforce headers on Core calls:
   - `X-Tenant-ID`
   - `X-Correlation-Id`
   - `Idempotency-Key` (write operations)
3. Add new columns in Cashier for Core linkage:
   - `cash_register_movement.core_transaction_id` (nullable, indexed)
   - optional `cash_register_movement.idempotency_key`

### Phase 1 - RH and org/material read delegation

Move read ownership first (Cashier stops being source of truth):

1. `person`
2. `admin_profile`
3. `cashier_profile`
4. `organization`
5. `agency`
6. `cash_register`
7. `cashier_agency_assignment`
8. `cashier_manage_cash_register`

Cashier action:
- Replace local CRUD reads/writes with Core APIs.
- Keep only cache/projection tables if needed for performance.

Endpoint impact in Cashier:
- `/api/users/*`
- `/api/organizations*`
- `/api/agencies*`
- `/api/cash-registers*`

### Phase 2 - Accounts and customers full delegation

Move financial master data:

1. `customer_profile`
2. `account`
3. `currency_denomination`
4. `attached_document` (for account/customer/person context)
5. `bill`, `bill_item` (if billing in Core)

Cashier action:
- No local balance truth.
- Deposit/withdraw/p2p/facture payment call Core ledger first.
- On Core success, persist operational movement locally.

Endpoint impact in Cashier:
- `/api/customers/search`
- `/api/cashier/customers`
- `/api/cashier/accounts`
- `/api/admin/customers`
- `/api/admin/accounts`
- billing endpoints if present

### Phase 3 - Session and movement semantic alignment

Keep in Cashier:
- `cash_register_session`
- `cash_register_movement`
- `event_ticketing_detail`
- `cash_reconciliation`
- `cash_register_event`

Required semantic changes:
1. `cash_register_movement.emitter_id` = Core source `account.id`
2. `cash_register_movement.recipient_id` = Core destination `account.id`
3. fill `core_transaction_id` from Core response
4. writes use `Idempotency-Key` for exactly-once behavior

### Phase 4 - Cutover and decommission local truth

1. Freeze writes to Core-owned local tables.
2. Run reconciliation checks:
   - account balances from Core vs cashier local aggregates
   - active cashier/session eligibility checks
3. Remove/disable old local services that still write:
   - local account updates
   - local customer updates
   - local RH/profile updates
4. Keep read-only fallback only during stabilization window.

## 8) Per-table migration checklist

For each Core-owned table:
1. **Map** old schema -> Core API contract.
2. **Backfill** Core with current data (one-time job).
3. **Dual-read** in Cashier (Core first, local fallback temporary).
4. **Dual-write block**: stop local write path.
5. **Validate** counts/checksums/business totals.
6. **Drop or archive** local table ownership.

Recommended order:
1. `organization`, `agency`
2. `person`, `admin_profile`, `cashier_profile`
3. `cash_register`, `cashier_agency_assignment`, `cashier_manage_cash_register`
4. `customer_profile`, `account`
5. `currency_denomination`
6. `attached_document`
7. `bill`, `bill_item`

## 9) Operational guardrails

- Reject operation if `X-Tenant-ID` missing or inconsistent with token tenant.
- Reject write if `Idempotency-Key` missing.
- Always propagate `X-Correlation-Id` across Core and Cashier logs.
- If Core write fails, Cashier must not finalize local financial movement.

## 10) Core API contract (full requests/responses)

All calls from Cashier to Core use:

- `Authorization: Bearer <service-token>`
- `X-Tenant-ID: <organization_id>`
- `X-Correlation-Id: <uuid>`
- `Idempotency-Key: <uuid>` for POST write operations only

### 10.1 Session authorization (agency admin opens session)

#### `POST /core/v1/cash-sessions/authorize-open`

Request body:
```json
{
  "organization_id": "org-uuid",
  "agency_id": "agency-uuid",
  "register_id": "register-uuid",
  "cashier_person_id": "cashier-person-uuid",
  "opened_by_admin_id": "admin-person-uuid",
  "requested_at": "2026-02-21T10:00:00Z"
}
```

Success response:
```json
{
  "success": true,
  "allowed": true,
  "reason_code": null,
  "data": {
    "organization_id": "org-uuid",
    "agency_id": "agency-uuid",
    "register_id": "register-uuid",
    "register_account_id": "register-account-uuid",
    "cashier_person_id": "cashier-person-uuid",
    "cashier_active": true,
    "cashier_role": "SALES_AGENT",
    "register_active": true
  }
}
```

Denied response:
```json
{
  "success": true,
  "allowed": false,
  "reason_code": "CASHIER_NOT_ASSIGNED_TO_AGENCY",
  "data": null
}
```

### 10.2 Cashier login/session eligibility

#### `POST /core/v1/cash-sessions/authorize-login`

Request body:
```json
{
  "organization_id": "org-uuid",
  "cashier_person_id": "cashier-person-uuid",
  "session_id": "cashier-session-uuid"
}
```

Success response:
```json
{
  "success": true,
  "allowed": true,
  "reason_code": null
}
```

### 10.3 Customers search (no local customer truth in Cashier)

#### `GET /core/v1/customers/search?q=<term>&limit=20`

Success response:
```json
{
  "success": true,
  "data": [
    {
      "customer_id": "customer-uuid",
      "person": {
        "id": "person-uuid",
        "full_name": "Jean Doe",
        "phone": "699000111"
      },
      "accounts": [
        {
          "account_id": "acc-1",
          "account_number": "ACC-123456",
          "accounting_account": "47110001",
          "is_active": true,
          "currency": "XAF"
        }
      ]
    }
  ]
}
```

### 10.4 Accounts search/details

#### `GET /core/v1/accounts/search?q=<term>&limit=20`

Success response:
```json
{
  "success": true,
  "data": [
    {
      "account_id": "acc-uuid",
      "account_number": "ACC-123456",
      "accounting_account": "47110001",
      "customer_id": "customer-uuid",
      "customer_name": "Jean Doe",
      "customer_phone": "699000111",
      "currency": "XAF",
      "is_active": true
    }
  ]
}
```

#### `GET /core/v1/accounts/{accountId}`

Success response:
```json
{
  "success": true,
  "data": {
    "account_id": "acc-uuid",
    "account_number": "ACC-123456",
    "accounting_account": "47110001",
    "currency": "XAF",
    "is_active": true,
    "available_balance": 90000
  }
}
```

### 10.5 Deposit (register account <- customer account)

#### `POST /core/v1/cash-operations/deposit`

Request body:
```json
{
  "operation_id": "op-uuid",
  "session_id": "session-uuid",
  "organization_id": "org-uuid",
  "agency_id": "agency-uuid",
  "register_id": "register-uuid",
  "register_account_id": "register-account-uuid",
  "customer_account_id": "customer-account-uuid",
  "amount": 15000,
  "currency": "XAF",
  "reference": "DEP-2026-0001",
  "reason": "Customer deposit",
  "performed_by_cashier_id": "cashier-person-uuid",
  "performed_at": "2026-02-21T10:05:00Z"
}
```

Success response:
```json
{
  "success": true,
  "data": {
    "core_transaction_id": "txn-uuid",
    "status": "posted",
    "business_type": "customer_deposit",
    "source_account_id": "customer-account-uuid",
    "destination_account_id": "register-account-uuid",
    "amount": 15000,
    "currency": "XAF",
    "posted_at": "2026-02-21T10:05:01Z"
  }
}
```

### 10.6 Withdrawal (register account -> customer account)

#### `POST /core/v1/cash-operations/withdraw`

Request body:
```json
{
  "operation_id": "op-uuid",
  "session_id": "session-uuid",
  "organization_id": "org-uuid",
  "agency_id": "agency-uuid",
  "register_id": "register-uuid",
  "register_account_id": "register-account-uuid",
  "customer_account_id": "customer-account-uuid",
  "amount": 10000,
  "currency": "XAF",
  "reference": "WDR-2026-0001",
  "reason": "Customer withdrawal",
  "performed_by_cashier_id": "cashier-person-uuid",
  "performed_at": "2026-02-21T10:10:00Z"
}
```

Success response:
```json
{
  "success": true,
  "data": {
    "core_transaction_id": "txn-uuid",
    "status": "posted",
    "business_type": "customer_withdrawal",
    "source_account_id": "register-account-uuid",
    "destination_account_id": "customer-account-uuid",
    "amount": 10000,
    "currency": "XAF",
    "posted_at": "2026-02-21T10:10:01Z"
  }
}
```

### 10.7 Customer to customer transfer (P2P)

#### `POST /core/v1/cash-operations/transfer`

Request body:
```json
{
  "operation_id": "op-uuid",
  "organization_id": "org-uuid",
  "source_account_id": "customer-acc-source-uuid",
  "destination_account_id": "customer-acc-destination-uuid",
  "amount": 5000,
  "currency": "XAF",
  "reference": "P2P-2026-0001",
  "reason": "P2P transfer",
  "performed_by_cashier_id": "cashier-person-uuid",
  "session_id": "session-uuid",
  "performed_at": "2026-02-21T10:20:00Z"
}
```

Success response:
```json
{
  "success": true,
  "data": {
    "core_transaction_id": "txn-uuid",
    "status": "posted",
    "business_type": "transfer",
    "source_account_id": "customer-acc-source-uuid",
    "destination_account_id": "customer-acc-destination-uuid",
    "amount": 5000,
    "currency": "XAF",
    "posted_at": "2026-02-21T10:20:01Z"
  }
}
```

### 10.8 Bill payment

#### `POST /core/v1/cash-operations/bill-payment`

Request body:
```json
{
  "operation_id": "op-uuid",
  "session_id": "session-uuid",
  "organization_id": "org-uuid",
  "agency_id": "agency-uuid",
  "register_id": "register-uuid",
  "register_account_id": "register-account-uuid",
  "bill_id": "bill-uuid",
  "payment_mode": "cash",
  "customer_account_id": null,
  "amount": 25000,
  "currency": "XAF",
  "reference": "BILL-2026-0001",
  "performed_by_cashier_id": "cashier-person-uuid",
  "performed_at": "2026-02-21T10:30:00Z"
}
```

Success response:
```json
{
  "success": true,
  "data": {
    "core_transaction_id": "txn-uuid",
    "status": "posted",
    "business_type": "bill_payment",
    "source_account_id": null,
    "destination_account_id": "register-account-uuid",
    "amount": 25000,
    "currency": "XAF",
    "posted_at": "2026-02-21T10:30:01Z"
  }
}
```

### 10.9 Register account resolution

#### `GET /core/v1/registers/{registerId}/account`

Success response:
```json
{
  "success": true,
  "data": {
    "register_id": "register-uuid",
    "register_account_id": "register-account-uuid",
    "agency_id": "agency-uuid",
    "organization_id": "org-uuid",
    "is_active": true
  }
}
```

### 10.10 Notification targets for agency admins

#### `GET /core/v1/agencies/{agencyId}/notification-targets?role=AGENCY_ADMIN`

Success response:
```json
{
  "success": true,
  "data": [
    {
      "person_id": "admin-person-uuid",
      "telegram_chat_id": "123456789",
      "telegram_bot_token": "123:ABC"
    }
  ]
}
```

## 11) Generic Core error response expected by Cashier

All Core errors should follow:

```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Insufficient account balance",
    "retryable": false
  },
  "timestamp": "2026-02-21T10:00:00Z"
}
```

Common codes:
- `UNAUTHORIZED`
- `TENANT_SCOPE_MISMATCH`
- `CASHIER_NOT_ASSIGNED_TO_AGENCY`
- `REGISTER_NOT_ACTIVE`
- `SESSION_NOT_ACTIVE`
- `ACCOUNT_NOT_FOUND`
- `ACCOUNT_INACTIVE`
- `INSUFFICIENT_FUNDS`
- `IDEMPOTENCY_CONFLICT`
