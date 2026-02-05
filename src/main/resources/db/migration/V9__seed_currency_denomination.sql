INSERT INTO currency_denomination (id, currency, value, label, sort_order, is_active)
VALUES
    ('1c6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a01', 'XAF', 10000, '10,000 XAF', 1, true),
    ('2c6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a02', 'XAF', 5000, '5,000 XAF', 2, true),
    ('3c6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a03', 'XAF', 2000, '2,000 XAF', 3, true),
    ('4c6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a04', 'XAF', 1000, '1,000 XAF', 4, true),
    ('5c6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a05', 'XAF', 500, '500 XAF', 5, true),
    ('6c6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a06', 'XAF', 200, '200 XAF', 6, true),
    ('7c6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a07', 'XAF', 100, '100 XAF', 7, true),
    ('8c6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a08', 'XAF', 50, '50 XAF', 8, true),
    ('9c6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a09', 'XAF', 25, '25 XAF', 9, true),
    ('ac6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a0a', 'XAF', 10, '10 XAF', 10, true),
    ('bc6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a0b', 'XAF', 5, '5 XAF', 11, true),
    ('cc6a6d6f-0b32-4a20-9c9f-9a1f6c0f6a0c', 'XAF', 1, '1 XAF', 12, true)
ON CONFLICT (id) DO NOTHING;
