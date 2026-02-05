CREATE UNIQUE INDEX IF NOT EXISTS ux_cash_register_ip_address
    ON cash_register (ip_address)
    WHERE ip_address IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cash_register_mac_address
    ON cash_register (mac_address)
    WHERE mac_address IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cash_register_sale_agent_bank_account
    ON cash_register (sale_agent_bank_account)
    WHERE sale_agent_bank_account IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_cash_register_sale_agent_accounting_account
    ON cash_register (sale_agent_accounting_account)
    WHERE sale_agent_accounting_account IS NOT NULL;
