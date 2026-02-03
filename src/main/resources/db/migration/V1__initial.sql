-- CreateTable
CREATE TABLE person (
    id TEXT NOT NULL,
    user_name TEXT NOT NULL,
    account_number TEXT,
    actif BOOLEAN NOT NULL DEFAULT true,
    user_first_name TEXT NOT NULL,
    telegram_chat_id TEXT,
    phone TEXT,
    mail TEXT,
    card_id TEXT,
    born_on TIMESTAMP(3),
    sexe TEXT,
    adresse TEXT,
    country TEXT,
    password TEXT NOT NULL,

    CONSTRAINT person_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE cashier_profile (
    id TEXT NOT NULL,
    person_id TEXT NOT NULL,
    town_list_chosen TEXT,
    work_town TEXT,
    hire_date TIMESTAMP(3),

    CONSTRAINT cashier_profile_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE admin_profile (
    id TEXT NOT NULL,
    person_id TEXT NOT NULL,
    office_adress TEXT,
    role_type TEXT NOT NULL DEFAULT 'superadmin',
    agency_id TEXT,
    organization_id TEXT,

    CONSTRAINT admin_profile_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE customer_profile (
    id TEXT NOT NULL,
    person_id TEXT NOT NULL,
    profession TEXT,
    date_of_joining TIMESTAMP(3),

    CONSTRAINT customer_profile_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE cash_register (
    id TEXT NOT NULL,
    cashier TEXT,
    user_id TEXT,
    agency_id TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    double_closing_count BOOLEAN NOT NULL DEFAULT false,
    justifiable_threshold DOUBLE PRECISION,
    create_on TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by TEXT NOT NULL,
    adress TEXT,
    country TEXT,
    town TEXT,
    neighborhood TEXT,
    ip_address TEXT,
    mac_address TEXT,
    image_url TEXT,
    min_open_time TEXT,
    max_close_time TEXT,

    CONSTRAINT cash_register_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE agency (
    id TEXT NOT NULL,
    name TEXT NOT NULL,
    country TEXT NOT NULL,
    town TEXT NOT NULL,
    neighborhood TEXT,
    address TEXT,
    location_hint TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    requires_admin_assignment BOOLEAN NOT NULL DEFAULT false,
    organization_id TEXT,
    create_on TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT agency_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE organization (
    id TEXT NOT NULL,
    name TEXT NOT NULL,
    country TEXT,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    create_on TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by TEXT,
    telegram_bot_token TEXT,

    CONSTRAINT organization_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE cashier_manage_cash_register (
    id TEXT NOT NULL,
    cash_register_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    day TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT cashier_manage_cash_register_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE cashier_agency_assignment (
    id TEXT NOT NULL,
    cashier_id TEXT NOT NULL,
    agency_id TEXT NOT NULL,
    start_on TIMESTAMP(3),
    end_on TIMESTAMP(3),
    assigned_on TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by TEXT,

    CONSTRAINT cashier_agency_assignment_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE cash_register_session (
    id TEXT NOT NULL,
    cash_register_id TEXT NOT NULL,
    state TEXT NOT NULL,
    open_on TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    open_by TEXT NOT NULL,
    close_on TIMESTAMP(3),
    close_by TEXT,
    theorical_initial_funds DECIMAL(65,30) NOT NULL DEFAULT 0,
    theorical_close_funds DECIMAL(65,30),
    previous_event_hash TEXT,
    is_locked BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT cash_register_session_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE account (
    id TEXT NOT NULL,
    client_id TEXT NOT NULL,
    account_number TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    create_on TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by TEXT,
    previous_event_hash TEXT,
    total_funds DOUBLE PRECISION NOT NULL DEFAULT 0,

    CONSTRAINT account_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE cash_register_event (
    id TEXT NOT NULL,
    session_id TEXT,
    account_id TEXT,
    type TEXT NOT NULL,
    idempotency TEXT,
    date_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    author_id TEXT,
    payload TEXT,
    subject_type TEXT,
    subject_id TEXT,
    hash TEXT,
    previous_hash TEXT,

    CONSTRAINT cash_register_event_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE cash_register_movement (
    id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    sense TEXT NOT NULL,
    amount DECIMAL(65,30) NOT NULL,
    reason TEXT,
    recipient_id TEXT,
    emitter_id TEXT,
    is_accounted BOOLEAN NOT NULL DEFAULT false,
    event_ticketing_details BOOLEAN NOT NULL DEFAULT false,
    external_reference TEXT,
    create_on TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT cash_register_movement_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE event_ticketing_detail (
    id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    connection_type TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    value DECIMAL(65,30) NOT NULL,
    total DECIMAL(65,30) NOT NULL,
    denomination_id TEXT,
    movement_id TEXT,

    CONSTRAINT event_ticketing_detail_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE cash_reconciliation (
    id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    physical_total DECIMAL(65,30) NOT NULL,
    theorical_total DECIMAL(65,30) NOT NULL,
    difference DECIMAL(65,30) NOT NULL,
    statut TEXT NOT NULL,
    justification TEXT,
    create_on TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by TEXT NOT NULL,
    check_on TIMESTAMP(3),
    check_by TEXT,

    CONSTRAINT cash_reconciliation_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE attached_document (
    id TEXT NOT NULL,
    objet_type TEXT NOT NULL,
    objet_id TEXT NOT NULL,
    file_name TEXT NOT NULL,
    type_mime TEXT NOT NULL,
    storage_url TEXT NOT NULL,
    upload_on TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    upload_by TEXT NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT false,
    is_deleted BOOLEAN NOT NULL DEFAULT false,

    CONSTRAINT attached_document_pkey PRIMARY KEY (id)
);

-- CreateTable
CREATE TABLE currency_denomination (
    id TEXT NOT NULL,
    currency TEXT NOT NULL,
    value DECIMAL(65,30) NOT NULL,
    label TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,

    CONSTRAINT currency_denomination_pkey PRIMARY KEY (id)
);

-- CreateIndex
CREATE UNIQUE INDEX person_user_name_key ON person(user_name);

-- CreateIndex
CREATE UNIQUE INDEX person_account_number_key ON person(account_number);

-- CreateIndex
CREATE UNIQUE INDEX person_telegram_chat_id_key ON person(telegram_chat_id);

-- CreateIndex
CREATE UNIQUE INDEX person_mail_key ON person(mail);

-- CreateIndex
CREATE UNIQUE INDEX cashier_profile_person_id_key ON cashier_profile(person_id);

-- CreateIndex
CREATE UNIQUE INDEX admin_profile_person_id_key ON admin_profile(person_id);

-- CreateIndex
CREATE UNIQUE INDEX customer_profile_person_id_key ON customer_profile(person_id);

-- CreateIndex
CREATE UNIQUE INDEX account_account_number_key ON account(account_number);

-- CreateIndex
CREATE UNIQUE INDEX cash_reconciliation_session_id_key ON cash_reconciliation(session_id);

-- AddForeignKey
ALTER TABLE cashier_profile ADD CONSTRAINT cashier_profile_person_id_fkey FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE admin_profile ADD CONSTRAINT admin_profile_person_id_fkey FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE admin_profile ADD CONSTRAINT admin_profile_agency_id_fkey FOREIGN KEY (agency_id) REFERENCES agency(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE admin_profile ADD CONSTRAINT admin_profile_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE customer_profile ADD CONSTRAINT customer_profile_person_id_fkey FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register ADD CONSTRAINT cash_register_user_id_fkey FOREIGN KEY (user_id) REFERENCES person(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register ADD CONSTRAINT cash_register_agency_id_fkey FOREIGN KEY (agency_id) REFERENCES agency(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register ADD CONSTRAINT cash_register_create_by_fkey FOREIGN KEY (create_by) REFERENCES person(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE agency ADD CONSTRAINT agency_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE organization ADD CONSTRAINT organization_create_by_fkey FOREIGN KEY (create_by) REFERENCES person(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cashier_manage_cash_register ADD CONSTRAINT cashier_manage_cash_register_cash_register_id_fkey FOREIGN KEY (cash_register_id) REFERENCES cash_register(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cashier_manage_cash_register ADD CONSTRAINT cashier_manage_cash_register_user_id_fkey FOREIGN KEY (user_id) REFERENCES person(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cashier_agency_assignment ADD CONSTRAINT cashier_agency_assignment_cashier_id_fkey FOREIGN KEY (cashier_id) REFERENCES person(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cashier_agency_assignment ADD CONSTRAINT cashier_agency_assignment_agency_id_fkey FOREIGN KEY (agency_id) REFERENCES agency(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register_session ADD CONSTRAINT cash_register_session_cash_register_id_fkey FOREIGN KEY (cash_register_id) REFERENCES cash_register(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register_session ADD CONSTRAINT cash_register_session_open_by_fkey FOREIGN KEY (open_by) REFERENCES person(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register_session ADD CONSTRAINT cash_register_session_close_by_fkey FOREIGN KEY (close_by) REFERENCES person(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE account ADD CONSTRAINT account_client_id_fkey FOREIGN KEY (client_id) REFERENCES customer_profile(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register_event ADD CONSTRAINT cash_register_event_session_id_fkey FOREIGN KEY (session_id) REFERENCES cash_register_session(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register_event ADD CONSTRAINT cash_register_event_account_id_fkey FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register_event ADD CONSTRAINT cash_register_event_author_id_fkey FOREIGN KEY (author_id) REFERENCES person(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register_movement ADD CONSTRAINT cash_register_movement_session_id_fkey FOREIGN KEY (session_id) REFERENCES cash_register_session(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_register_movement ADD CONSTRAINT cash_register_movement_create_by_fkey FOREIGN KEY (create_by) REFERENCES person(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE event_ticketing_detail ADD CONSTRAINT event_ticketing_detail_session_id_fkey FOREIGN KEY (session_id) REFERENCES cash_register_session(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE event_ticketing_detail ADD CONSTRAINT event_ticketing_detail_denomination_id_fkey FOREIGN KEY (denomination_id) REFERENCES currency_denomination(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE event_ticketing_detail ADD CONSTRAINT event_ticketing_detail_movement_id_fkey FOREIGN KEY (movement_id) REFERENCES cash_register_movement(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_reconciliation ADD CONSTRAINT cash_reconciliation_session_id_fkey FOREIGN KEY (session_id) REFERENCES cash_register_session(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_reconciliation ADD CONSTRAINT cash_reconciliation_create_by_fkey FOREIGN KEY (create_by) REFERENCES person(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE cash_reconciliation ADD CONSTRAINT cash_reconciliation_check_by_fkey FOREIGN KEY (check_by) REFERENCES person(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE attached_document ADD CONSTRAINT attached_document_upload_by_fkey FOREIGN KEY (upload_by) REFERENCES person(id) ON DELETE RESTRICT ON UPDATE CASCADE;
