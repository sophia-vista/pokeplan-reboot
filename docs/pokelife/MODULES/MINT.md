# MINT — Personal Finance Module

**Version:** 1.0  
**Status:** Approved module specification  
**Last consolidated:** 5 November 2026  
**Source of truth:** MINT requirements and module-specific behavior  
**Related:** `../PRODUCT_BIBLE.md`, `../IMPLEMENTATION_GUIDE.md`, `../FEATURES/DESIGN_SYSTEM.md`, `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture`, `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`, `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`

> MINT is PokéLife's private, local-first personal finance region. This document owns MINT's workflows, data, integration behavior, privacy boundaries, progression events, presentation adaptations and acceptance criteria. Shared design, reminder delivery, data lifecycle and integration foundations remain owned by their respective shared specifications.

## 1. Purpose

MINT provides a dependable personal finance workspace, designed PHP-first for Philippine financial services while supporting configurable currencies.

It helps the user understand:
1. How much money is recorded across their accounts.
2. Where money is held.
3. Where it is going.
4. Which obligations are coming due.
5. How current spending compares with their plans.
6. Which balances or imports may be stale or incomplete.

MINT must be useful without bank integrations and without Pokémon progression. It is a personal finance manager, not a banking application, payment processor, investment adviser, credit-scoring service or tax-preparation product.

### 1.1 Product principles

- **Trust over automation:** reliable manual records are better than unsupported automatic connections.
- **One financial truth:** each account, transaction, bill and budget has one canonical owner.
- **Honest freshness:** every externally sourced balance has provenance and a timestamp.
- **Fast capture:** ordinary expenses should be recordable with minimal interaction.
- **Useful context:** prioritize available money, upcoming obligations and spending patterns over dashboard ornament.
- **Local-first privacy:** financial records remain private and under the user's control.
- **Non-punitive game layer:** financial outcomes never determine rewards or Pokémon access.
- **No false precision:** distinguish recorded, estimated, pending, imported and provider-confirmed values.

## 2. Scope and boundaries

### 2.1 MINT owns

- Financial account definitions and account groups.
- Account opening balances and locally recorded balance snapshots.
- Manual financial transactions and transaction classification.
- Imported transaction records and import provenance.
- Transfers, splits, refunds, fees and reconciliation records.
- Categories and merchant labels.
- Budgets, budget periods and savings targets.
- Bills, bill occurrences and payment status.
- Financial summaries, reports and exports.
- Financial-provider connection metadata and local mapping.
- Currency conversion records used in financial reporting.

### 2.2 MINT does not own

| Capability | Owner |
|---|---|
| Tasks, calendar and general planning | PLAN |
| Shared reminder scheduling and Android delivery | `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` |
| Physical health and medical information | HEAL |
| Household inventory and maintenance | HOME |
| Contacts and relationship records | LINK |
| General-purpose calculators and converters | GEAR |
| Shared Pokémon collection, Bag and Poké currency | Shared Pokémon/economy systems |
| Global profile, security preferences and backup lifecycle | Shared app/architecture services |

A bill can link to a PLAN task when the user chooses. MINT remains the canonical owner of the bill and payment state. Completing a PLAN task does not itself mark the bill paid.

### 2.3 Explicit non-goals

- Executing bank transfers, bill payments or purchases.
- Storing banking passwords, PINs, OTPs or card security codes.
- Unsupported bank scraping or security circumvention.
- Investment portfolio trading, stock brokerage, crypto custody or financial recommendations.
- Credit scoring, loan eligibility or automated debt collection.
- Shared household finance or multi-user account access in the initial scope.
- Mandatory online accounts, cloud sync, telemetry or subscriptions.
- Financial-performance-based Pokémon rewards.

## 3. Information architecture

MINT has six primary destinations. Overview is the default landing screen. Quick capture is globally available within MINT.

| Destination | Purpose |
|---|---|
| Overview | Financial position, available money, upcoming obligations and recent activity |
| Accounts | Banks, e-wallets, cash, cards, assets and liabilities |
| Activity | Transactions, transfers, search, filters and reconciliation |
| Budgets | Spending plans, category limits and savings targets |
| Bills | Recurring and one-time obligations |
| Reports | Cash flow, spending patterns and historical comparisons |

### 3.1 Navigation and quick actions

Quick actions:
- Add expense.
- Add income.
- Transfer.
- Add bill.
- Reconcile account.
- Import statement.

The primary quick action is **Add expense**. Do not make the user navigate through multiple screens to record an ordinary purchase.

### 3.2 Overview hierarchy

Show information in this order, adapting to available data:
1. Total net position, if the user has configured enough account and liability data for a meaningful calculation.
2. Available money, with its calculation basis visible.
3. Upcoming bills and obligations.
4. Budget progress.
5. Recent activity.
6. Optional trends or review prompts.

Avoid displaying every metric simultaneously. Empty states should offer the next useful action, not a tour of every feature.

### 3.3 Financial value privacy

Provide a global MINT privacy toggle to hide monetary values across every MINT destination, widgets and supported MINT surfaces. When hidden:
- Replace values consistently rather than merely blurring them.
- Preserve layout and non-sensitive context such as bill count or transaction date.
- Do not reveal values through accessibility descriptions, notification previews or chart labels.
- Respect the setting when navigating between MINT screens.
- Do not expose hidden values in screenshots generated by in-app sharing.

## 4. Accounts

### 4.1 Supported account types

- Bank account: savings, checking/current and other user-defined deposit accounts.
- E-wallet: GCash, Maya and other user-defined wallets.
- Digital bank account.
- Cash wallet or physical cash.
- Credit card.
- Loan or other liability.
- Other user-defined asset or liability account.

Provider names are labels, not proof of a live integration.

### 4.2 Account fields

A `FinancialAccount` may include:
- Stable ID.
- Display name.
- Provider/institution label, optional.
- Account type and asset/liability classification.
- Currency code.
- Opening balance and opening date.
- Current locally recorded balance.
- Optional masked account suffix or user-defined identifier.
- Optional account group.
- Optional color/icon from the shared component system.
- Connection/source reference, if applicable.
- Status: active, archived or closed.
- Created/updated timestamps.

Never request or store full card numbers, CVV, banking passwords, PINs or OTPs. Account identifiers are optional; masked suffixes should be sufficient for recognition.

### 4.3 Account groups

Users may organize accounts into groups such as:
- Daily spending.
- Bills.
- Savings.
- Emergency reserve.
- Cash.
- Credit and liabilities.
- Custom groups.

Groups are organizational only. They do not own balances and must never duplicate an account's value.

### 4.4 Balance model and freshness

A balance is a value observed or recorded at a particular time, not an eternal property of an account.

Every `BalanceSnapshot` stores:
- Account reference.
- Amount and currency.
- Effective/observed timestamp.
- Source: manual, imported statement, provider sync, reconciliation or adjustment.
- Optional source reference and import batch.
- Optional note.

Show the last-updated time and source wherever freshness affects a decision.

Freshness labels:
- **Live:** only when the provider explicitly confirms a current balance at request time.
- **Synced:** successfully retrieved at a known timestamp.
- **Stale:** the snapshot is older than the source's expected refresh interval or a refresh has failed.
- **Manual:** entered or adjusted by the user.
- **Imported:** derived from a statement or file.

Do not label a cached API response as live. A failed synchronization never overwrites a valid local snapshot.

### 4.5 Assets, liabilities and available money

- Assets include positive balances in owned cash/deposit/wallet accounts and other configured assets.
- Liabilities include outstanding credit-card balances, loans and other recorded debts.
- Net position is assets minus liabilities, converted only when a valid rate is available and clearly identified.
- Credit limits are not assets or available cash.
- A credit card's available credit is not money owned by the user.

Available money is a configurable estimate, not a provider-guaranteed balance. It may be calculated from selected liquid accounts minus user-designated reserved funds and upcoming obligations. Show the calculation basis and exclusions. Do not subtract the same bill reserve twice or assume that all assets are immediately spendable.

### 4.6 Account lifecycle

Create, edit, archive, close and restore where supported. Archiving hides an account from default active views but preserves history. Closing records an effective date and prevents new ordinary transactions unless explicitly reopened. Deletion is restricted when history exists; offer archive instead. Destructive deletion must explain dependent records and require confirmation.

## 5. Transactions and activity

### 5.1 Transaction types

- Expense.
- Income.
- Transfer.
- Refund/reversal.
- Fee or interest.
- Balance adjustment.

Pending and posted are transaction states, not separate transaction types. Imported and manually entered are provenance values, not transaction types.

### 5.2 Transaction fields

A `Transaction` may include:
- Stable ID.
- Type.
- Amount in original currency.
- Currency code.
- Effective date/time.
- Account reference; transfer source and destination references for transfers.
- Category and optional subcategory.
- Payee/merchant label.
- Short description.
- Optional private note.
- Status: pending, posted, reconciled, excluded or voided as appropriate.
- Provenance: manual, import, provider or adjustment.
- Optional import/source reference.
- Optional bill occurrence reference.
- Optional receipt attachment reference.
- Created/updated timestamps.

Store monetary values using exact decimal arithmetic or integer minor units with explicit currency scale. Never use binary floating-point arithmetic for money. Preserve original amount/currency even when a converted reporting value is calculated.

### 5.3 Fast expense entry

Default flow:
1. Enter amount.
2. Select account.
3. Select category.
4. Save.

Transaction type defaults to expense. Date defaults to now. Merchant, notes, split allocation, receipt and advanced fields remain optional and secondary. Remember sensible recent choices only when doing so is predictable and user-controlled.

The amount field must support decimal separators and locale-aware formatting without changing the underlying numeric value. Validation must reject zero/negative amounts for ordinary income/expense entries unless a specific adjustment/refund model permits them.

### 5.4 Income and expense behavior

- Expenses reduce the relevant asset balance or increase a liability when charged to a credit card.
- Income increases the relevant asset balance or reduces a liability only when the transaction semantics explicitly represent that.
- A refund references or can be matched to an earlier expense when possible; it must not be silently treated as ordinary income.
- Fees and interest remain distinguishable from ordinary purchases.
- Excluded transactions remain visible in history but are omitted from applicable spending calculations, with clear labeling.

### 5.5 Transfers

A transfer is one logical event with two account legs:
- Source account decreases.
- Destination account increases.
- Currency conversion/fees may create explicit additional legs or fee records.
- Transfers between owned accounts are excluded from income and expense totals.
- The two legs must be committed atomically.
- Editing or deleting a transfer updates both legs consistently.
- A failed or interrupted save must not leave a one-sided transfer.

Transfers between different currencies preserve source amount, destination amount, rate, fee and rate provenance.

### 5.6 Credit cards and liabilities

A card purchase increases the card's outstanding liability and records the purchase expense once. Paying the card reduces the associated bank/cash asset and the card liability; it is a transfer/settlement, not a second expense. Fees and interest are separate expense transactions. Card limit, outstanding balance, statement balance and payment due amount must not be conflated.

### 5.7 Splits, duplicates and edits

Support split transactions where one transaction is allocated across multiple categories. Split totals must equal the transaction amount in the original currency, subject to explicit rounding rules.

Provide:
- Search by merchant, note, category, account and amount.
- Filters by type, status, date, account and category.
- Bulk categorization with preview and undo where safe.
- Duplicate detection for imports using provider IDs where available and conservative composite matching otherwise.
- Transaction templates for repeated manual entries.
- Edit, void, exclude and delete with clear effects on balances, budgets, bills and reports.

Do not silently merge records based only on similar amount/date. Ambiguous duplicates require review.

### 5.8 Reconciliation

A reconciliation session compares MINT's records with a statement or provider snapshot.

It supports:
- Statement closing date and ending balance.
- Imported statement transactions.
- Suggested matches with confidence.
- Manual matching/unmatching.
- Unmatched imported and local transactions.
- Explicit adjustment record when the user accepts a balance difference.
- Completion state and audit history.

Imported records remain identifiable as imported. Reconciliation does not silently replace manual history or erase unmatched transactions.

## 6. Categories and merchants

Categories are MINT-owned and support:
- Expense/income classification.
- Parent/child hierarchy where useful.
- User-defined names and icons.
- Active/archived state.
- Optional default category rules.
- Optional budget association.

Seed a compact practical category set, localized for Philippine use, but allow editing and deletion only when no dependent history is lost. Deleting a category with transactions requires reassignment or archive.

Merchant names are descriptive labels, not a separate contact system. Do not sync them into LINK.

## 7. Budgets and savings targets

### 7.1 Budgets

Support:
- Overall spending budget.
- Category budget.
- Weekly, monthly, annual or custom period.
- Start/end dates and recurrence.
- Optional rollover.
- Optional alert thresholds.
- Optional account/category scope.
- Active, paused and archived states.

A budget stores the planned amount and period. Actual spending is derived from eligible posted transactions. Transfers are excluded. Refund treatment is explicit and consistent (normally offsets the originating category's spending). Pending transactions may be shown separately or included by user preference, but must never be silently mixed with posted actuals.

### 7.2 Budget display

Show:
- Planned amount.
- Actual eligible spending.
- Remaining amount.
- Percent used.
- Period dates.
- Optional recent transactions.

Use semantic status labels and accessible indicators. Exceeding a budget is informative, not a failure state. Avoid shame-based language, punitive visuals or mandatory notifications.

### 7.3 Rollover

Rollover is opt-in per budget. Define whether positive remaining amount, overspend or both carry forward. Do not assume all budgets should roll over. Period calculations must be deterministic across time-zone changes and month-length differences.

### 7.4 Savings targets

Savings targets are planning records, not separate bank accounts. A target may link to one or more accounts and define a target amount/date, but its progress is derived from user-designated contributions or explicitly selected account balances. Avoid double-counting money across multiple targets. Savings targets never create artificial deposits or transfer records.

## 8. Bills and obligations

### 8.1 Bill fields

A `Bill` may include:
- Stable ID and name/payee.
- Fixed expected amount or variable amount.
- Currency.
- Due date and recurrence definition.
- Optional category.
- Optional intended payment account.
- Reminder preference.
- Optional reference/notes.
- Active, paused or archived state.

A `BillOccurrence` represents one expected obligation for one due period. It tracks due date, expected amount, actual amount if known, status and optional linked transaction.

### 8.2 Lifecycle

`Upcoming → Due → Paid / Partially paid / Overdue / Skipped`

Status transitions must be explicit and reversible where practical. A paid occurrence can link to an actual transaction. Marking a bill paid does not fabricate a transaction. Recording a transaction does not automatically mark a bill paid unless a reliable match is confirmed or the user explicitly chooses the action.

Partial payments retain remaining due amount. Variable bills allow the actual amount to be entered when known. Skipping an occurrence must not silently delete the recurring bill definition.

### 8.3 Recurrence

Support weekly, monthly, yearly and custom schedules where the recurrence engine can handle them reliably. Define behavior for end-of-month dates, leap years, time-zone changes, paused recurrence and edited future occurrences. Never rewrite historical bill occurrences when a future schedule changes.

### 8.4 PLAN relationship

MINT owns bill records, occurrences and payment state. A user may create/link a PLAN task such as “Pay internet bill.” The link stores references only. PLAN task completion does not confirm payment. MINT's payment confirmation does not require a PLAN task. Avoid duplicate reminders for the same obligation unless the user deliberately enables both and understands the distinction.

## 9. Reports and financial review

Reports should answer practical questions:
- Income versus expenses.
- Spending by category and merchant.
- Spending by account.
- Monthly cash flow.
- Budget performance.
- Savings contributions.
- Liability and debt trends.
- Recurring obligations.
- Historical period comparisons.

All reports support relevant date range and account/category filters. Do not create charts when a concise total or list is clearer. Clearly label incomplete periods, missing accounts, stale balances, imported-only data and estimated conversions.

### 9.1 Review workflow

An optional weekly/monthly review may surface:
- Unreviewed imported transactions.
- Accounts with stale balances.
- Upcoming bills.
- Budget progress.
- Unmatched reconciliation entries.

Users may complete only relevant sections. Skipping a review creates no penalty and does not block normal finance use.

## 10. Philippine provider integrations

### 10.1 Research priorities

Investigate legitimate supported access for:
- GCash.
- Maya.
- MariBank.
- GoTyme.
- UnionBank.

Priority indicates research importance, not confirmed API availability or a promise that live balance/transaction access exists.

### 10.2 Integration hierarchy

Use, in order of preference:
1. Official supported APIs and documented consumer authorization flows.
2. Established aggregation providers with legitimate coverage, user consent and acceptable terms.
3. Supported statement/CSV imports.
4. Explicitly opt-in, locally processed notification parsing only where lawful, permitted by platform/provider terms and technically reliable.
5. Manual entry.

Do not:
- Ask for or store banking passwords, PINs, CVV or OTPs.
- Scrape authenticated app screens.
- Circumvent app/device protections.
- Use accessibility automation as a hidden extraction mechanism.
- Claim a provider connection is live without end-to-end verification.
- Promise automatic updates when only manual statement import is supported.

### 10.3 Provider connection metadata

A `FinancialProviderConnection` stores only what is needed for a supported integration:
- Provider identifier.
- Connection status.
- Granted scope/consent metadata.
- Linked local account mappings.
- Last attempt and last successful update.
- Error category suitable for user display.
- Provider connection reference/token only if required by a legitimate supported flow, stored using platform-appropriate secure storage and never logged.
- Revocation/disconnect state.

A connection failure does not disable manual MINT workflows.

### 10.4 Notification parsing

If implemented after feasibility and policy review:
- Explicit opt-in per provider/source.
- Narrow allowlist of supported notification formats.
- Parse locally on device.
- Show a proposed transaction for user review before posting.
- Retain only the minimum parsed fields needed after confirmation.
- Do not persist raw notification text by default.
- Provide immediate revocation and deletion of derived pending records.
- Never infer a balance from incomplete notification history.

## 11. Currency and exchange rates

PHP is the default currency for a new PH-oriented setup. Users can configure a different primary/reporting currency and create accounts in other currencies.

- Store each original transaction in its original currency.
- Never overwrite original amounts with converted values.
- Store rate, rate direction, provider/source, timestamp and fee where relevant.
- Distinguish transaction-time rate from current reporting rate.
- Offline reports use the last-known rate and show its timestamp; do not imply it is current.
- Missing rates must not silently become 1:1.
- Fees and spreads are explicit.
- Rounding follows the currency's minor-unit rules.
- Financial conversions are owned by MINT records; GEAR may share a generic conversion service but does not own transaction conversion history.

## 12. Reminders

MINT uses `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` for scheduling, quiet hours, focus deferral, Android delivery, reboot recovery, duplicate prevention and action routing.

MINT owns:
- Whether a bill occurrence requests a reminder.
- Bill-specific reminder timing and enablement.
- The meaning and content of a bill notification.
- Validation of the bill occurrence before opening or acting.
- Cancellation/rescheduling when the bill changes, is paid, skipped, archived or deleted.

Suggested choices: due date, one day before, several days before, custom timing, or no reminder. Defaults must be configurable and conservative. Do not generate repeated daily overdue notifications by default.

Notifications are private by default:
- Generic lock-screen text.
- No amounts, account balances, merchant names or full account identifiers.
- Detailed notification content requires explicit user preference.
- No financial details in analytics or diagnostic logs.

A bill reminder can open the relevant MINT occurrence. It must not silently execute a payment or mark the obligation paid.

## 13. Privacy, security and data lifecycle

Financial data is sensitive and receives explicit boundaries.

### 13.1 Data isolation

- Financial records remain in MINT's local data domain.
- No amount, merchant, provider, account name, balance, debt, transaction description or financial circumstance enters WORLD, launcher, Pokémon profiles or shared analytics.
- No financial record is included in ordinary diagnostics, crash logs or event telemetry.
- Shared services receive only the minimum metadata required for their function.
- No mandatory account, cloud profile, remote synchronization or third-party analytics.

### 13.2 Access protection

MINT may offer optional biometric/device-authentication gating using supported Android APIs. The gate must not create a second password database or make the data unrecoverable. Recovery and backup behavior are specified by shared architecture.

### 13.3 Backup, export and deletion

Follow shared data lifecycle specifications for encryption, backup, restore, migration, export and erase. MINT-specific requirements:
- Export accounts, transactions, categories, budgets, bills and reconciliation records in documented formats.
- Preserve original currency, provenance and timestamps.
- Allow users to disconnect integrations.
- Disconnecting a provider does not delete manually created records.
- Allow deletion of imported provider data separately from user-created data where feasible.
- Full MINT deletion must include local financial records and derived indexes/caches according to the shared erase contract.
- Never claim deletion from a provider or external aggregation service unless that operation is actually supported and confirmed.

### 13.4 Attachments

Receipt/document attachments are private MINT data. Use the shared attachment/storage policy, avoid embedding sensitive metadata in filenames, and ensure export/backup/delete operations include attachment references and bytes according to the user's selection.

## 14. Offline behavior and reliability

MINT must remain fully functional without internet access.

| Capability | Offline behavior |
|---|---|
| Accounts and account groups | Full local functionality |
| Manual transactions and transfers | Full local functionality |
| Budgets and bills | Full local functionality |
| Reports | Calculated from local records; disclose missing/stale inputs |
| Balance history | Available locally |
| CSV/statement import | Available from local files |
| Export | Available locally |
| Provider synchronization | Unavailable until supported connection returns |
| Online exchange rates | Last-known rate with source/time, or unavailable |
| Reconciliation | Local records and locally imported statements |

Reliability requirements:
- Atomic paired transfer writes.
- Exact monetary arithmetic and currency scale.
- Idempotent import processing.
- Stable transaction/source IDs.
- Duplicate detection that never silently discards ambiguous entries.
- Explicit pending/posted distinction.
- No silent currency conversion or balance overwrite.
- Recoverable import/reconciliation after process termination.
- Versioned, non-destructive schema migrations.
- Backup/restore validation and integrity checks.
- Time-zone-safe recurring bill and budget periods.

## 15. Data architecture

Logical domain entities (physical table/collection names may differ):
- `FinancialAccount`
- `AccountGroup`
- `BalanceSnapshot`
- `Transaction`
- `TransactionSplit`
- `Category`
- `Budget`
- `BudgetPeriod`
- `SavingsTarget`
- `Bill`
- `BillOccurrence`
- `Reconciliation`
- `ReconciliationMatch`
- `ImportBatch`
- `FinancialProviderConnection`
- `ExchangeRateSnapshot`

All records have stable IDs and appropriate created/updated timestamps. Records requiring history use soft deletion/archive or append-only audit records as appropriate. Repository APIs must not expose mutable shared objects across module boundaries.

### 15.1 Ownership relationships

| Record/event | Owner |
|---|---|
| Account and account group | MINT |
| Local balance snapshot | MINT |
| Provider's external balance/transaction | Provider; MINT stores permitted sourced snapshot/import |
| Manual transaction | MINT |
| Budget and budget period | MINT |
| Bill and occurrence | MINT |
| Reminder request | Shared scheduler; source meaning remains MINT |
| PLAN payment task | PLAN |
| Reward ledger entry | Shared economy |
| Pokémon ownership | Shared Pokémon system |

### 15.2 Financial invariants

1. A transfer has balanced source/destination legs, with explicit conversion/fee treatment.
2. A transaction has one currency and a positive magnitude; direction is represented by type/legs, not ambiguous signed values.
3. Split allocations sum to the parent amount within defined minor-unit rounding.
4. A credit-card purchase is an expense once; settlement is not a second expense.
5. An imported transaction retains source identity and import provenance.
6. A bill marked paid has an explicit payment state and optional transaction link; it does not invent a transaction.
7. Balance snapshots are observations, not destructive edits to transaction history.
8. Reconciliation adjustments are explicit records.
9. A budget's actual amount is derived from eligible transactions and cannot mutate them.
10. Currency conversion never destroys the original amount/currency.

## 16. Shared Pokémon progression: Ledger Research

### 16.1 Regional identity

MINT's acquisition identity is **Ledger Research**. The region's researchers catalogue and organize discoveries. Pokémon acquisition is tied to completing useful financial organization activities, not to financial performance.

This identity is distinct from HEAL's Field Research and HOME's Habitat Discovery.

### 16.2 Eligible activity families

Eligible activity events may include:
- Completing an optional financial review.
- Reconciling an account.
- Reviewing/categorizing a meaningful batch of imported transactions.
- Creating or completing a budget review milestone.
- Reviewing upcoming bills as part of a periodic review.
- Completing a defined periodic financial organization milestone.

Opening MINT, viewing balances, entering a purchase, making a payment, receiving income, transferring money, changing an account balance, connecting a bank or increasing savings are not reward-bearing actions.

Do not reward creating empty records, repeated edits, repeatedly opening the same review, or artificial micro-actions. Define eligibility thresholds and caps in the shared economy/progression ledger, not in UI code.

### 16.3 Minimal progression event contract

MINT may emit only approved abstract events, for example:
- `financial_review_completed`
- `account_reconciled`
- `budget_review_completed`
- `bill_review_completed`

Events contain stable event ID, source module, opaque source reference, event type, timestamp and only the metadata required for idempotency/eligibility.

Never include:
- Monetary values or currency.
- Provider or account names.
- Merchant, category, transaction description or note.
- Income, spending, savings or debt totals.
- Bill names, payees or due amounts.
- Imported statement content.
- Whether the user has a particular account, debt or income source.

### 16.4 Reward and collection ownership

- Shared reward ledger evaluates and records eligible events.
- Shared Bag/Poké currency remains authoritative.
- Shared Pokémon collection owns recruited Pokémon.
- MINT does not create its own currency, Bag or duplicate Pokémon records.
- Rewards are generous and predictable, with multiple opportunities for meaningful progress.
- No streak decay, missed-review penalty, expiring reward or pressure to disclose financial data.
- User can disable MINT progression without disabling financial features.
- Reward delivery is idempotent and safe under retries, restore and migration.

## 17. Visual presentation

MINT follows `../FEATURES/DESIGN_SYSTEM.md`. It uses the shared PMD dialogue-box visual language at **utility presentation** intensity.

### 17.1 MINT-specific adaptations

- Use dialogue panels for important summaries, confirmations and contextual guidance, not as a wrapper for every value or transaction row.
- Use compact, legible lists/tables for activity and account history.
- Use restrained pixel-inspired borders and shared surfaces.
- Use system sans-serif for financial values, long text, forms and notes.
- Use tabular numerals for aligned monetary values.
- Use charts only when they improve understanding; always provide an accessible textual equivalent.
- Use semantic colors for income, expense, liability and warning states; never rely on color alone.
- Keep optional Pokémon art contextual and secondary to financial tasks.
- No Pokémon art may obscure values, controls, privacy indicators or financial status.
- Provide global hide-values behavior across all screens.
- Dark and light themes must maintain readable contrast.

### 17.2 Overview density

The overview is not a conventional bank dashboard. It prioritizes a small number of meaningful summaries and the next useful action. Avoid excessive cards, nested dialogue boxes, redundant captions, multiple competing charts and duplicated totals.

### 17.3 State design

Define concise empty, loading, stale, offline, import error, connection error, reconciliation-needed and partial-data states. Errors must explain what remains available and the next safe action. Never use alarming or shame-based copy for budget overruns or debt.

## 18. Implementation phases

### P0 — Financial foundation
- Confirm independent MINT domain and repository boundaries.
- Define money/currency types and exact arithmetic.
- Implement local persistence, schema versioning, migrations and integrity checks.
- Define sensitive-data handling, backup and export integration.
- Define provider interfaces without promising live coverage.

### P0 — Accounts and transactions
- Account lifecycle, account groups, opening balances and snapshots.
- Income, expense, transfer, refund, fee and adjustment records.
- Category management, fast entry, search and filters.
- Atomic transfer and credit-card accounting invariants.

### P0 — Budgets and bills
- Budget periods, calculations, rollover and optional savings targets.
- Bill recurrence, occurrences, partial payments and payment linking.
- Shared reminder contract integration.

### P1 — Overview and reports
- Overview summaries, available-money calculation and privacy mode.
- Cash-flow, category and account reports.
- Stale/incomplete data disclosure and financial review.

### P1 — Import, reconciliation and export
- CSV/statement import, source provenance and duplicate review.
- Reconciliation sessions and explicit adjustments.
- Complete local export and recovery tests.

### P1 — Progression
- Ledger Research event contract.
- Shared reward ledger integration and privacy tests.
- Disable-progression behavior.

### P2 — Provider integrations
- Provider-by-provider feasibility research.
- Official API/aggregation adapter only after documented access, consent, security and coverage validation.
- Notification parsing only after platform/provider policy and technical review.
- Connection diagnostics, revocation and recovery.

Provider integrations must not block complete manual finance functionality.

## 19. Acceptance criteria

### Accounts and balances
- [ ] Users can create, edit, group, archive and close supported account types.
- [ ] Opening balances and timestamped snapshots are preserved.
- [ ] Assets, liabilities and net position are calculated correctly.
- [ ] Credit limits are never counted as assets or cash.
- [ ] Every external balance displays source and freshness.
- [ ] Failed sync cannot erase or overwrite a valid local snapshot.

### Transactions
- [ ] Expense, income, transfer, refund, fee and adjustment are supported.
- [ ] Ordinary expense capture is fast and does not require optional fields.
- [ ] Transfers are atomic and excluded from income/expense totals.
- [ ] Credit-card purchases and settlements are counted correctly.
- [ ] Split allocations reconcile to the parent amount.
- [ ] Duplicate imports are detected without silently deleting ambiguous records.
- [ ] Search, filters, editing, exclusion, voiding and deletion behave consistently.
- [ ] Monetary calculations use exact arithmetic.

### Budgets and bills
- [ ] Budget actuals derive only from eligible transactions.
- [ ] Transfers do not inflate spending.
- [ ] Refund treatment is consistent.
- [ ] Rollover is explicit and configurable.
- [ ] Bill recurrence handles month-end and leap-year cases.
- [ ] Paid status does not fabricate a transaction.
- [ ] Payment links do not create double-counted expenses.
- [ ] Bill reminders use the shared feature and never execute payments.

### Integrations and offline
- [ ] MINT works fully without a bank connection or internet.
- [ ] Provider support is claimed only after end-to-end verification.
- [ ] No password, PIN, CVV or OTP scraping/storage exists.
- [ ] Imported records retain provenance and timestamps.
- [ ] Failed integrations preserve local records.
- [ ] Offline exchange rates are timestamped and never presented as current.
- [ ] Import/reconciliation can recover after interruption.

### Privacy and security
- [ ] Hide-values mode covers all MINT surfaces and accessibility output.
- [ ] Sensitive data is excluded from logs, analytics and game services.
- [ ] Notifications are generic by default.
- [ ] Provider disconnection does not delete manual records.
- [ ] Export, backup, restore and erase pass tests.
- [ ] Optional access protection follows shared Android security architecture.

### Pokémon progression
- [ ] Ledger Research is MINT's only acquisition identity.
- [ ] Only approved administrative activities emit reward events.
- [ ] Events contain no financial amounts, provider names or private records.
- [ ] Shared reward ledger and collection remain authoritative.
- [ ] Retries cannot duplicate rewards.
- [ ] Disabling progression leaves MINT fully functional.

### Presentation
- [ ] Shared PMD dialogue-box system is used without a separate MINT design language.
- [ ] Dense activity records remain readable and easy to scan.
- [ ] Light/dark themes and larger font scales work.
- [ ] Privacy mode is consistent.
- [ ] Empty, loading, offline, stale and error states are clear.
- [ ] No redundant cards, captions or decorative Pokémon compete with finance tasks.

## 20. Audit baseline and reuse policy

The supplied repository archive `pokeplan-reboot-main (5).zip`, version `0.123.0`, was inspected for financial-domain implementation. No first-class MINT module or financial account, transaction, budget, bill or provider-connection domain was found. The incidental `nature-mint.png` asset is unrelated.

This is a static archive audit, not a claim that a build or runtime test was performed. Reinspect the current working branch before implementation because source may have changed after the baseline archive.

### Reuse
- Shared Pokémon collection, reward ledger and economy interfaces.
- Shared app navigation/module registration patterns where they meet current architecture.
- Shared database, repository, backup and migration foundations after review.
- Shared design-system components and semantic tokens.
- Shared reminder scheduler and notification contracts.
- Generic currency formatting/conversion primitives from GEAR only if they preserve MINT's source/rate provenance and do not transfer ownership of financial records.

### Refactor
- Isolate progression event delivery from financial repositories.
- Ensure exact monetary arithmetic and atomic transactions.
- Add provenance-aware import and reconciliation pipelines.
- Ensure backup/export and erase cover financial data safely.
- Make provider adapters explicit, permission-scoped and independently revocable.

### Replace or avoid
- Do not add a large set of financial fields to a legacy global save object.
- Do not store MINT records in PLAN, WORLD or GEAR repositories.
- Do not reuse legacy cloud synchronization as a financial-data dependency.
- Do not create another Pokémon inventory or currency.
- Do not implement bank scraping through accessibility automation or credential capture.

## 21. Documentation ownership

| Document | Owns |
|---|---|
| Product Bible | Product-wide MINT purpose, module map and non-negotiable constraints |
| This `MODULES/MINT.md` | MINT workflows, domain, integrations, privacy adaptation, progression and acceptance |
| `FEATURES/DESIGN_SYSTEM.md` | Shared PMD dialogue-box visual language and components |
| `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md#9-shared-reminder-and-notification-architecture` | Shared scheduling, delivery, quiet hours, focus deferral and notification routing |
| `ARCHITECTURE/DATA_AND_MIGRATIONS.md` | Shared persistence, migration, backup, restore and erase foundations |
| `ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md` | Shared provider, permission, secure storage and privacy architecture |
| Implementation Guide | Audit status, implementation order and exit gates |

MINT-specific documents may link to shared specifications but must not duplicate their global contracts.

## 22. As built (0.141.0, REP-014): the manual slice

*Built and tested on the JVM, compiled for Android; **not run on a device**. Nothing here is a provider integration: there is no bank, e-wallet or statement connection, and Settings says so.*

**What exists.** Section 3's destinations as a menu with Overview on top: **Accounts** (4: bank, e-wallet, digital bank, cash, card, loan, other asset and liability; a short optional suffix and group; archive, close, reopen; deletion refused when history exists), **Activity** (5: expense, income, transfer, refund, fee, balance adjustment; fast entry of amount, account, category; pending, posted, excluded, voided; search and type filters; edit, exclude, void, delete), **Budgets** (7: monthly or weekly, overall or one category, planned, spent, left or over, percent), **Bills** (8: one time, weekly, monthly, yearly; the month-end and leap-day rules; paid, part paid, skipped, undo; paying creates no transaction), **Review** (9: this month's money in and out, spending by category, the reconcile flow of 5.8 and the weekly review), and **Settings** (Ledger Research, hide amounts, default currency, export and erase by kind). Overview shows net position per currency, available money with its basis (liquid accounts less known bills due in 14 days; a card's balance is never cash) and the next bills.

**Money.** `Money` keeps integer minor units with a per-currency scale (JPY, KRW, VND have none) and parses typed amounts (`1,250.50`, `1.250,50`, `12,5`) without floating point or rounding (more decimals than the currency has is refused). Amounts are shown as `PHP 1,250.50` (the code, not a symbol, so no font has to carry a glyph). A transaction has one currency, equal to its account's; a transfer between different currencies is **not supported yet** (5.5), and no amount is ever added across currencies. A balance is derived (`opening + posted records`), pending is shown apart, a liability's balance is what is owed, and an adjustment moves the displayed balance in the direction it says.

**Ledger Research (section 16), as built** *(numbers are *(tune)* starting values; `RewardRules.VERSION` 18, `docs/economy.md`)*:
- **On by default** (as is every acquisition method, owner, 8 November 2026), off in Settings without touching a record (16.4). Only two things can pay: an **account reconciled** (the user compares a statement figure with Mint's; a difference is saved only as an explicit adjustment record they accept; once per account a day, 2 a day; the account must have real transactions) and a **finished weekly review** (once a week; there must be an open account and a transaction). Entering a purchase, income, a transfer, a balance, a budget, a bill, an empty account or a repeat of either action never pays.
- The event is exactly `MintEvent`: a stable `id` (`mint:<kind>:<record id>`), `module` ("mint"), an opaque `ref`, the broad `kind` (`account_reconciled` or `financial_review_completed`) and the time. `MintStoreTest.reconcilingPaysOnceAndTheEventCarriesNothingButTheContract` and `MintTablesTest.noAmountOrNameOfMintEverReachesTheSharedTables` prove no amount, currency, account, merchant, category or note reaches the event, the shared save, the reward log, the ledger or the Pokémon tables. The game remembers paid ids (`AppData.mintPaid`, the newest 1000) and the record keeps `paid`.
- A payment is 3 Rare Candies (reconciled) or 5 (review), a little friendship for Mint's lead, a trainer point and points towards a **Ledger Egg** (2 and 3; 4 fill the meter), hatched into Mint's party from the **Mint Region** (`tools/species/regions/mint.txt`). Mint's party perks (Candy finder, Friendship gain, capped at +25% a bucket) change only what Ledger Research pays.
- **First-run tutorial** (POKEMON_ECOSYSTEM.md 10.2.1): a 5-second tally gives one Mint Region Pokémon into Mint's party, leading it, once per save; it creates no record, event, meter point or cap use.

**Data (section 15).** `mint_*` tables from the start (schema 8): `mint_account`, `mint_category`, `mint_transaction`, `mint_budget`, `mint_bill`, `mint_bill_payment`, `mint_reconciliation`, `mint_review` (each row `key, position, day, json`) and `mint_state` (settings); `MintTables` reads, exports or erases one family alone. Mint is in the daily backup and the backup bundle (`BackupBundle.KNOWN`); restoring a backup made before Mint existed leaves Mint alone. **Encryption (owner delegated the choice, 8 November 2026; built in 0.141.0).** Every Mint row (each record's JSON and each setting) is sealed with **AES-256-GCM** under a key made inside the **Android Keystore** (non-exportable, in hardware where the phone has it; `KeystoreMintKey`, `AesGcmCipher`, `RowCipher`), a fresh random IV per row; the stored `day` column is left empty so a row shows no amount, name or date (only its random key and its position). Every copy of Mint that leaves its tables is sealed the same way: the daily backup and a backup file carry Mint as one sealed text, and what a restore or a failed read keeps aside is sealed. So the database, its backups and Android's own cloud backup of the app's files hold Mint only as text that opens on this phone. The cost, stated plainly: **a backup of Mint opens only on a phone that has the key**; a backup restored on another phone keeps Mint sealed in a `aside:mint:locked:*` document (nothing is deleted or overwritten) and Mint starts empty there. Mint's own **Export** (Settings) is the way to move records: it is a readable file and says so. If the Keystore cannot give the key, Mint does not start that run, is kept in memory only and is never written in clear. **Not done:** a passphrase-sealed backup that opens on any phone (the next step: a user passphrase through PBKDF2 into the same AES-GCM), the optional access gate (13.2), and attachments.

**Not built, not offered:** bill reminders (section 12; the shared scheduler has a `MINT` module slot and nothing feeds it), splits and bulk categorisation (5.7), reconciliation sessions with imported statements (5.8), rollover and savings targets (7.3, 7.4), account groups as their own records (a group is a label), foreign currency, exchange rates, CSV and statement import, provider connections and notification parsing (10), the budget and bill review events (`budget_review_completed`, `bill_review_completed`), charts, and the Android widget for Mint.

## Shared Pokémon ecosystem contract

MINT's acquisition identity is **Ledger Research**. MINT owns eligibility for approved financial-organization actions; the shared Pokémon ecosystem owns reward settlement, canonical Egg/owned-Pokémon records, Pokédex, inventory and progression. MINT has an independent party referencing canonical owned Pokémon IDs. Financial amounts, balances, transaction details, merchants, account/provider identifiers and payment data are excluded from reward events. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

MINT persistence, transaction integrity, balance reconciliation, import deduplication, offline behavior and strict financial-data privacy boundaries must follow [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Use synthetic financial fixtures only.
