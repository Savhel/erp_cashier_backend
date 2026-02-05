CREATE INDEX IF NOT EXISTS idx_cash_register_agency_id
    ON cash_register (agency_id);

CREATE INDEX IF NOT EXISTS idx_agency_organization_id
    ON agency (organization_id);

CREATE INDEX IF NOT EXISTS idx_cash_register_session_register_open_on
    ON cash_register_session (cash_register_id, open_on DESC);

CREATE INDEX IF NOT EXISTS idx_cash_register_session_open_by_open_on
    ON cash_register_session (open_by, open_on DESC);

CREATE INDEX IF NOT EXISTS idx_cash_register_session_open_by_state
    ON cash_register_session (open_by, state);

CREATE INDEX IF NOT EXISTS idx_cash_register_session_open_on
    ON cash_register_session (open_on DESC);

CREATE INDEX IF NOT EXISTS idx_cashier_agency_assignment_cashier_assigned_on
    ON cashier_agency_assignment (cashier_id, assigned_on DESC);
