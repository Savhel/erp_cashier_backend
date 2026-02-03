# Database schema (Prisma)

```prisma
generator client {
  provider = "prisma-client-js"
  output   = "../node_modules/.prisma/client"
  previewFeatures = ["driverAdapters"]
}

datasource db {
  provider = "sqlite"
}

// --- Users & Roles ---

model Person {
  id              String   @id @default(uuid())
  user_name       String   @unique
  account_number  String?  @unique
  actif           Boolean  @default(true)
  user_first_name String
  telegram_chat_id String? @unique
  telegram_bot_token String?
  phone           String?
  mail            String?  @unique
  card_id         String?
  born_on         DateTime?
  sexe            String?
  adresse         String?
  country         String?
  password        String

  // Role specific profiles (1-to-1 optional)
  cashierProfile  CashierProfile?
  adminProfile    AdminProfile?
  customerProfile CustomerProfile?

  // Relations
  createdRegisters CashRegister[] @relation("CreatedBy")
  openedSessions   CashRegisterSession[] @relation("OpenedBy")
  closedSessions   CashRegisterSession[] @relation("ClosedBy")
  
  // Auditing
  createdEvents    CashRegisterEvent[]
  createdMovements CashRegisterMovement[]
  createdReconciliations CashReconciliation[] @relation("CreatedBy")
  checkedReconciliations CashReconciliation[] @relation("CheckedBy")
  uploadedDocuments AttachedDocument[]

  // Cashier Management
  managedRegisters CashierManageCashRegister[]
  assignedRegisters CashRegister[] @relation("AssignedCashier")
  agencyAssignments CashierAgencyAssignment[] @relation("CashierAgencyAssignments")
  createdOrganizations Organization[] @relation("OrganizationCreator")
}

model CashierProfile {
  id               String   @id @default(uuid())
  personId         String   @unique
  person           Person   @relation(fields: [personId], references: [id], onDelete: Cascade)
  
  town_list_chosen String? // JSON string
  work_town        String?
  hire_date        DateTime?
  organization_id String
  organization    Organization @relation(fields: [organization_id], references: [id])
  base_agency_id  String
  baseAgency      Agency @relation("BaseAgency", fields: [base_agency_id], references: [id])
}

model AdminProfile {
  id            String @id @default(uuid())
  personId      String @unique
  person        Person @relation(fields: [personId], references: [id], onDelete: Cascade)
  
  office_adress String?
  role_type     String @default("superadmin") // superadmin, organization_admin or agency_admin
  agency_id     String?
  organization_id String?
  monitor_all_agencies Boolean @default(true)
  monitor_agency_ids   String?
  monitor_all_registers Boolean @default(true)
  monitor_register_ids String?
  agency        Agency? @relation("AgencyAdmins", fields: [agency_id], references: [id])
  organization  Organization? @relation("OrganizationAdmins", fields: [organization_id], references: [id])
}

model CustomerProfile {
  id              String    @id @default(uuid())
  personId        String    @unique
  person          Person    @relation(fields: [personId], references: [id], onDelete: Cascade)
  
  profession      String?
  date_of_joining DateTime?
  
  accounts        Account[]
}

// --- Cash Registers ---

model CashRegister {
  id                   String   @id @default(uuid())
  cashier              String?  // Name or identifier
  user_id              String?  // Current assigned user (optional direct link)
  assignedCashier      Person?  @relation("AssignedCashier", fields: [user_id], references: [id])
  agency_id            String?
  agency               Agency?  @relation(fields: [agency_id], references: [id])
  is_active            Boolean  @default(true)
  double_closing_count Boolean  @default(false)
  justifiable_threshold Float?
  create_on            DateTime @default(now())
  create_by            String
  creator              Person   @relation("CreatedBy", fields: [create_by], references: [id])
  
  adress               String?  // JSON { GPS, adresse_complete }
  country              String?
  town                 String?
  neighborhood         String?
  ip_address           String?
  mac_address          String?
  image_url            String?
  min_open_time        String?
  max_close_time       String?
  sale_agent_bank_account String?
  sale_agent_accounting_account String?

  sessions             CashRegisterSession[]
  managedBy            CashierManageCashRegister[]
}

model Agency {
  id            String   @id @default(uuid())
  name          String
  country       String
  town          String
  neighborhood  String?
  address       String?
  location_hint String?
  is_active     Boolean  @default(true)
  requires_admin_assignment Boolean @default(false)
  organization_id String
  organization  Organization @relation(fields: [organization_id], references: [id])
  create_on     DateTime @default(now())
  cashRegisters CashRegister[]
  admins        AdminProfile[] @relation("AgencyAdmins")
  cashierAssignments CashierAgencyAssignment[]
  baseCashiers  CashierProfile[] @relation("BaseAgency")
}

model Organization {
  id            String   @id @default(uuid())
  name          String
  country       String?
  description   String?
  is_active     Boolean  @default(true)
  create_on     DateTime @default(now())
  create_by     String?
  creator       Person?  @relation("OrganizationCreator", fields: [create_by], references: [id])
  agencies      Agency[]
  admins        AdminProfile[] @relation("OrganizationAdmins")
  telegram_bot_token String?
  cashiers      CashierProfile[]
}

model CashierManageCashRegister {
  id               String   @id @default(uuid())
  cash_register_id String
  cashRegister     CashRegister @relation(fields: [cash_register_id], references: [id])
  user_id          String
  person           Person   @relation(fields: [user_id], references: [id])
  day              DateTime @default(now())
}

model CashierAgencyAssignment {
  id          String   @id @default(uuid())
  cashier_id  String
  cashier     Person   @relation("CashierAgencyAssignments", fields: [cashier_id], references: [id])
  agency_id   String
  agency      Agency   @relation(fields: [agency_id], references: [id])
  start_on    DateTime?
  end_on      DateTime?
  assigned_on DateTime @default(now())
  assigned_by String?
}

// --- Sessions ---

model CashRegisterSession {
  id                      String   @id @default(uuid())
  cash_register_id        String
  cashRegister            CashRegister @relation(fields: [cash_register_id], references: [id])
  
  state                   String   // "ouverte", "fermee", "en_cloture"
  open_on                 DateTime @default(now())
  open_by                 String
  opener                  Person   @relation("OpenedBy", fields: [open_by], references: [id])
  
  close_on                DateTime?
  close_by                String?
  closer                  Person?  @relation("ClosedBy", fields: [close_by], references: [id])
  
  theorical_initial_funds Decimal  @default(0)
  theorical_close_funds   Decimal? // Calculated
  previous_event_hash     String?
  is_locked               Boolean  @default(false)

  events                  CashRegisterEvent[]
  movements               CashRegisterMovement[]
  reconciliation          CashReconciliation?
  ticketingDetails        EventTicketingDetail[]
}

// --- Accounting & Movements ---

model Account {
  id                  String   @id @default(uuid())
  client_id           String
  customer            CustomerProfile @relation(fields: [client_id], references: [id])
  account_number      String?  @unique
  
  is_active           Boolean  @default(true)
  create_on           DateTime @default(now())
  create_by           String?
  previous_event_hash String?
  total_funds         Float    @default(0)
  accounting_account  String?
  
  events              CashRegisterEvent[]
}

model CashRegisterEvent {
  id            String   @id @default(uuid())
  session_id    String?
  session       CashRegisterSession? @relation(fields: [session_id], references: [id])
  
  account_id    String?
  account       Account? @relation(fields: [account_id], references: [id])
  
  type          String   // "ouverture", "mouvement", "cloture", "recomptage", "login", "logout", "crud", etc.
  idempotency   String?
  date_time     DateTime @default(now())
  author_id     String?
  author        Person?   @relation(fields: [author_id], references: [id])
  
  payload       String?  // JSON
  subject_type  String?
  subject_id    String?
  hash          String?
  previous_hash String?
}

model CashRegisterMovement {
  id                      String   @id @default(uuid())
  session_id              String
  session                 CashRegisterSession @relation(fields: [session_id], references: [id])
  
  sense                   String   // "entree", "sortie", "transfert"
  amount                  Decimal
  reason                  String?
  recipient_id            String?
  emitter_id              String?
  is_accounted            Boolean  @default(false)
  event_ticketing_details Boolean  @default(false)
  external_reference      String?
  create_on               DateTime @default(now())
  create_by               String
  creator                 Person   @relation(fields: [create_by], references: [id])
  is_deleted              Boolean  @default(false)

  ticketingDetails        EventTicketingDetail[]
}

model EventTicketingDetail {
  id               String   @id @default(uuid())
  session_id       String
  session          CashRegisterSession @relation(fields: [session_id], references: [id])
  
  connection_type  String   // "session_ouverture", "session_fermeture", "mouvement"
  quantity         Int
  value            Decimal
  total            Decimal
  
  denomination_id  String?
  denomination     CurrencyDenomination? @relation(fields: [denomination_id], references: [id])

  movement_id      String?
  movement         CashRegisterMovement? @relation(fields: [movement_id], references: [id])
}

model CashReconciliation {
  id              String   @id @default(uuid())
  session_id      String   @unique
  session         CashRegisterSession @relation(fields: [session_id], references: [id])
  
  physical_total  Decimal
  theorical_total Decimal
  difference      Decimal
  statut          String   // "a_valider", "valide", "rejete"
  justification   String?
  
  create_on       DateTime @default(now())
  create_by       String
  creator         Person   @relation("CreatedBy", fields: [create_by], references: [id])
  
  check_on        DateTime?
  check_by        String?
  checker         Person?  @relation("CheckedBy", fields: [check_by], references: [id])
}

// --- Supporting Tables ---

model AttachedDocument {
  id              String   @id @default(uuid())
  objet_type      String   // "mouvement", "session", "rapprochement"
  objet_id        String   // ID of the related object (polymorphic-like)
  
  file_name       String
  type_mime       String
  storage_url     String
  upload_on       DateTime @default(now())
  upload_by       String
  uploader        Person   @relation(fields: [upload_by], references: [id])
  
  is_verified     Boolean  @default(false)
  is_deleted      Boolean  @default(false)
}

model CurrencyDenomination {
  id        String   @id @default(uuid())
  currency  String   // "XAF"
  value     Decimal
  label     String
  order     Int
  is_active Boolean  @default(true)
  
  ticketingDetails EventTicketingDetail[]
}
```
