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
- Optional color/icon from the shared component system. Built as a card style (`FinancialAccount.card`, 0.200.0): six cosmetic styles (Plain, Blue, Green, Gold, Violet, Slate), saved as ids with the colours chosen in the UI; an unknown id reads and saves as plain. It never changes a balance, rule or report, and needs no schema change (the field is inside the account's sealed row, default empty).
- Optional **receive-money QR image** (owner, 15 November 2026: a wallet's own "scan to send money to me" code, such as a GCash InstaPay QR). One small PNG per account, picked with Android's Photo Picker, shrunk on the phone to at most 600 px and 64 KB, and kept as base64 inside the account's own sealed row (`FinancialAccount.receiveQr`): no new table, no file outside the sealed store, so it is covered by the sealing, the lock gate, backup and delete like any other account field. Shown on the account's card as "Receive money" with Replace and Remove. Tapping it opens it **full screen** on a white page, as large as the screen allows, with the screen at full brightness and kept on while it is open (the window's own override, undone on close; no permission, no system setting changed), and a **Share** action hands the PNG to Android's share sheet as `receive-qr.png` (the file name never carries the account's name; it is written to the cache's `exports/` folder, where the older files are removed each time), so the player chooses where it goes. Both are off while "hide values" is on. It is never uploaded by the app, never read for its contents, never appears in a reward event, a notification, a widget or a log, and "hide values" covers it. Cosmetic and a convenience only: it changes no balance, rule or report. A QR is a payment address, so it is not a card number, CVV or password (section 4.2 last paragraph), but the user is told it is private.
- Connection/source reference, if applicable.
- Status: active, archived or closed.
- Created/updated timestamps.

First run: the new-account form is titled "Where is this money?", the name field shows "GCash" as a placeholder, a "?" key opens one dialogue defining the eight account types, and saving the first account returns to the Mint overview, where Expense, Income and Transfer are the next actions.

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

A budget stores the planned amount and period; both can be edited afterwards, and a change that would make a second budget for the same category, currency and period is refused (0.173.0). Actual spending is derived from eligible posted transactions. Transfers are excluded. Refund treatment is explicit and consistent (normally offsets the originating category's spending). Pending transactions may be shown separately or included by user preference, but must never be silently mixed with posted actuals.

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

A settled occurrence (paid or skipped) stays reachable for 60 days after its due day (`MintRules.settled`, the Bills section's "Settled lately"): it can be settled again with another amount, day or status, which replaces the earlier settlement, or taken back to unsettled (0.173.0).

### 8.3 Recurrence

Support weekly, monthly, yearly and custom schedules where the recurrence engine can handle them reliably. Define behavior for end-of-month dates, leap years, time-zone changes, paused recurrence and edited future occurrences. Never rewrite historical bill occurrences when a future schedule changes.

### 8.3A Recurring income and expenses (owner, 15 November 2026, REP-039 first slice)

A salary, allowance or subscription that repeats is a **recurring entry**, and it uses the same schedule, occurrences and lifecycle as a bill (8.1 to 8.3): a `Bill` has a direction, `income = false` for something to pay and `income = true` for money expected in. Nothing new is stored and nothing is posted by itself:
- **Due item you confirm.** On the due day an occurrence is Due (and Overdue after), exactly as for a bill. "Paid, record it" (bill) or "Received, record it" (income) asks for the account and the real amount (the expected amount is the default), then writes the transaction (an expense for a bill, an income for a recurring income) and links it both ways in one save. The app never posts a transaction on its own, including when it was closed on the due day, because a wrong amount must never post silently.
- **Skip** and **Mark settled without a transaction** work as for a bill; skipping never deletes the schedule. A partly received amount keeps the remainder due (8.2).
- An income's category is an income category and its transaction is an income, so it counts as money received (7.1) and never as a refund. The direction is fixed when the entry is created and cannot be edited afterwards, as the schedule cannot (8.3).
- Weekly, monthly and yearly only; month-end and leap-day follow the bill rule (a 31st falls on the last day of a shorter month, 29 February on 28 February in a common year). Custom schedules are not built.
- A reminder (section 12) says only that something is coming up, for an income as for a bill, and never an amount.

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

The one attachment built is an account's receive-money QR image (4.2), kept inside the account's sealed row because it is small (at most 64 KB); a receipt attachment (5.2) is not built and, being larger, would be a sealed file in app-private storage with a metadata table (DATA_AND_MIGRATIONS.md). Receipt/document attachments are private MINT data. Use the shared attachment/storage policy, avoid embedding sensitive metadata in filenames, and ensure export/backup/delete operations include attachment references and bytes according to the user's selection.

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

Status is tracked in `../IMPLEMENTATION_BACKLOG.md`. Order:

- **P0, foundation:** independent MINT domain and repositories; exact money and currency types; local persistence, schema versioning, migrations and integrity checks; sensitive-data handling, backup and export; provider interfaces that promise no live coverage. Accounts and transactions (lifecycle, groups, opening balances, snapshots, all transaction types, categories, fast entry, search, filters, atomic transfers and credit-card invariants). Budgets and bills (periods, rollover, optional savings targets, recurrence, occurrences, partial payments, payment linking, shared reminder contract).
- **P1:** Overview (available money, privacy mode), cash-flow, category and account reports, stale-data disclosure, financial review; CSV/statement import with provenance and duplicate review, reconciliation sessions with explicit adjustments, complete local export and recovery tests; Ledger Research event contract, shared reward ledger integration and privacy tests, disable-progression behaviour.
- **P2, provider integrations:** provider-by-provider feasibility; an official API or aggregation adapter only after documented access, consent, security and coverage validation; notification parsing only after policy and technical review; connection diagnostics, revocation and recovery.

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

## 20. Reuse policy

Reuse the shared Pokémon collection, reward ledger and economy interfaces, navigation and module registration, database, backup and migration foundations, design-system components and tokens, and the shared reminder scheduler. GEAR's generic currency formatting or conversion primitives only if they preserve MINT's source/rate provenance and do not take ownership of financial records.

Avoid: adding financial fields to a legacy global save object; storing MINT records in PLAN, WORLD or GEAR repositories; legacy cloud sync as a financial-data dependency; another Pokémon inventory or currency; bank scraping through accessibility automation or credential capture. Keep progression event delivery isolated from financial repositories, imports provenance-aware, backup/export/erase covering financial data, and provider adapters explicit, permission-scoped and independently revocable.

## 21. Documentation ownership

Product-wide MINT purpose and constraints: `../PRODUCT_BIBLE.md`. Visual language: `../FEATURES/DESIGN_SYSTEM.md`. Shared reminders, providers, permissions, secure storage and privacy: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`. Persistence, migration, backup, restore and erase: `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`. Delivery status: `../IMPLEMENTATION_BACKLOG.md`. This file owns MINT workflows, domain, integrations, privacy adaptation, progression and acceptance; do not duplicate the shared contracts.

## 22. As built: the manual slice

*Not run on a device. No bank or e-wallet is connected, and the pages say so; statement-file import is in 22A.*

**What exists.** Section 3's destinations as a menu with Overview on top: **Accounts** (4: bank, e-wallet, digital bank, cash, card, loan, other asset and liability; a short optional suffix and group; archive, close, reopen; deletion refused when history exists), **Activity** (5: expense, income, transfer, refund, fee, balance adjustment; fast entry of amount, account, category; pending, posted, excluded, voided; search and type filters; edit, exclude, void, delete), **Budgets** (7: monthly or weekly, overall or one category, planned, spent, left or over, percent), **Bills** (8: one time, weekly, monthly, yearly; the month-end and leap-day rules; paid, part paid, skipped, undo; paying creates no transaction), **Review** (9: this month's money in and out, spending by category, the reconcile flow of 5.8 and the weekly review), and **Settings** (Ledger Research, hide amounts, default currency, export and erase by kind). Overview shows net position per currency, available money with its basis (liquid accounts less known bills due in 14 days; a card's balance is never cash) and the next bills.

**Money.** `Money` keeps integer minor units with a per-currency scale (JPY, KRW, VND have none) and parses typed amounts (`1,250.50`, `1.250,50`, `12,5`) without floating point or rounding (more decimals than the currency has is refused). Amounts are shown as `₱1,250.50` for the peso (the app's Pokémon DP font carries the sign since 0.166.0) and as `USD 1,250.50`, the code, for every other currency (a code is never ambiguous, and no font has to carry its symbol). Categories (spending and income) are added, renamed, put away and deleted in Settings, and a new one can be made from the entry form (0.166.0). A record can be made for any day up to today with the shared date picker (0.166.0); editing a record's date, account or category is still **not built**. A transaction has one currency, equal to its account's; a transfer between different currencies is **not supported yet** (5.5), and no amount is ever added across currencies. A balance is derived (`opening + posted records`), pending is shown apart, a liability's balance is what is owed, and an adjustment moves the displayed balance in the direction it says.

**Ledger Research (section 16), as built** *(numbers are *(tune)* starting values; `RewardRules.VERSION` 18, `docs/economy.md`)*:
- **On by default** (as is every acquisition method, owner, 8 November 2026), off in Settings without touching a record (16.4). Only two things can pay: an **account reconciled** (the user compares a statement figure with Mint's; a difference is saved only as an explicit adjustment record they accept; once per account a day, 2 a day; the account must have real transactions) and a **finished weekly review** (once a week; there must be an open account and a transaction). Entering a purchase, income, a transfer, a balance, a budget, a bill, an empty account or a repeat of either action never pays.
- The event is exactly `MintEvent`: a stable `id` (`mint:<kind>:<record id>`), `module` ("mint"), an opaque `ref`, the broad `kind` (`account_reconciled` or `financial_review_completed`) and the time. `MintStoreTest.reconcilingPaysOnceAndTheEventCarriesNothingButTheContract` and `MintTablesTest.noAmountOrNameOfMintEverReachesTheSharedTables` prove no amount, currency, account, merchant, category or note reaches the event, the shared save, the reward log, the ledger or the Pokémon tables. The game remembers paid ids (`AppData.mintPaid`, the newest 1000) and the record keeps `paid`.
- A payment is 3 Rare Candies (reconciled) or 5 (review), a little friendship for Mint's lead, a trainer point and points towards a **Ledger Egg** (2 and 3; 4 fill the meter), hatched into Mint's party from the **Mint Region** (`tools/species/regions/mint.txt`). Mint's party perks (Candy finder, Friendship gain, capped at +25% a bucket) change only what Ledger Research pays.
- **First-run tutorial** (POKEMON_ECOSYSTEM.md 10.2.1): a 5-second tally gives one Mint Region Pokémon into Mint's party, leading it, once per save; it creates no record, event, meter point or cap use.

**Data (section 15).** `mint_*` tables from the start (schema 8): `mint_account`, `mint_category`, `mint_transaction`, `mint_budget`, `mint_bill`, `mint_bill_payment`, `mint_reconciliation`, `mint_review` (each row `key, position, day, json`) and `mint_state` (settings); `MintTables` reads, exports or erases one family alone. Mint is in the daily backup and the backup bundle (`BackupBundle.KNOWN`); restoring a backup made before Mint existed leaves Mint alone. **Encryption (owner delegated the choice, 8 November 2026).** Every Mint row (each record's JSON and each setting) is sealed with **AES-256-GCM** under a key made inside the **Android Keystore** (non-exportable, in hardware where the phone has it; `KeystoreMintKey`, `AesGcmCipher`, `RowCipher`), a fresh random IV per row; the stored `day` column is left empty so a row shows no amount, name or date (only its random key and its position). Every copy of Mint that leaves its tables is sealed the same way: the daily backup and a backup file carry Mint as one sealed text, and what a restore or a failed read keeps aside is sealed. So the database, its backups and Android's own cloud backup of the app's files hold Mint only as text that opens on this phone. The cost, stated plainly: **a backup of Mint opens only on a phone that has the key**; a backup restored on another phone keeps Mint sealed in a `aside:mint:locked:*` document (nothing is deleted or overwritten) and Mint starts empty there. Mint's own **Export** (Settings) is the way to move records: it is a readable file and says so. If the Keystore cannot give the key, Mint does not start that run, is kept in memory only and is never written in clear. **Portable backup.** A backup *file* can instead carry Mint sealed under a **passphrase** the player chose (`p1.`: PBKDF2-HMAC-SHA256, 600,000 rounds, random salt and stored count, into AES-256-GCM; `PassphraseSealer`, `Pbkdf2Sealer`, `BackupBundle.portable` and `unlock`; at least 10 characters), so it opens on any phone given the passphrase. Only a file the player asks to be portable is made this way; the tables and the daily backups stay sealed to the phone's key. Restoring asks for the passphrase, a wrong one changes nothing, and the restored Mint is sealed to the new phone's key as it is written. The passphrase is never stored and a lost one cannot be recovered; a Mint backup sealed by another phone's key cannot be re-sealed here. **Access gate (13.2).** Optional (Settings → Privacy): the phone's own screen lock (`KeyguardManager` confirm-credential, `DeviceGate`, `AndroidDeviceGate`) before the Mint screens, again after the app or Mint was left for 30 seconds; a door only, no key or password of its own, so it cannot lock anyone out; unavailable without a screen lock and on desktop and web. It does not guard the files (the sealing does) or Settings → Backups. **Not done:** attachments other than the receive QR (22B), a gate on the Pokémon side of Mint's party, and a device check of either.

**Bill reminders (section 12).** Off until turned on (Settings), then one reminder per occurrence, `0` to `7` days before the due day at a chosen hour, through the shared scheduler (`MintReminders`, `MintReminderAdapter`, `BillReminders`): the quiet hours and focus sessions hold it, an overdue bill is not repeated, a settled, paused, deleted or switched-off bill stops asking, the only button is "Tomorrow" (never "Paid"), and the request holds only the bill's opaque id and its due day. The lock screen always says "A bill is coming up"; the bill's name shows only after unlocking and only if the user allowed it; never an amount. Custom timing beyond the chips and a per-bill timing are not built. **Account edit.** An account's name, type (within its side), provider, group, last digits and opening balance can be edited, and its currency while it has no records.

**Not built, not offered:** splits and bulk categorisation (5.7), reconciliation sessions with imported statements (5.8), rollover and savings targets (7.3, 7.4), account groups as their own records (a group is a label), foreign currency, other attachments (22B built the receive QR only), exchange rates, CSV and statement import, provider connections and notification parsing (10), the budget and bill review events (`budget_review_completed`, `bill_review_completed`), charts, and the Android widget for Mint.

**Audit fixes.** A transfer's default destination is the one shown (same currency only). Activity pages its list ("Show more", 50 at a time). A bill's first due day is picked with the shared date picker; its name and amount can be edited (the schedule stays, 8.3); every settlement offers an undo, and a part payment covering the expected amount is saved as paid. `MintRules.balances` is one pass over the records. **Open for the owner** (not changed): Exclude and Void do the same thing today (neither counts in a balance, a budget or a report; 5.4 reads as if an excluded record should still move the balance); Archive and Close do the same thing (4.6 gives closing an effective date, which is not kept); `Transaction.billId`, `billDueDay` and `refundOf` and a bill's account and category are stored but nothing sets them; marking a bill paid and recording its expense are two separate entries (8.2 allows one explicit action that does both).

## 22A. As built: statement import and provider status

*Not run on a device.*

**Classification** (`INTEGRATIONS_AND_PRIVACY.md` section 2): the statement file is a *user-assisted import*. GCash, Maya, MariBank, GoTyme and UnionBank are *unsupported or unverified*: the due diligence of section 7.1 (a documented personal-account API, independent developer access in the Philippines, production approval for an individual app, scopes and terms) **has not been done**, so none is offered, and the page says "no verified way to read a personal account". Reading payment notifications is not built (section 7.3: separate privacy review first). Nothing logs in, scrapes, uses Accessibility, or asks for a password, PIN, CVV or code.

**Desk research:** no documented route for an individual developer's app to read a user's own transactions was found for GCash, Maya, GoTyme or MariBank; UnionBank has a business-oriented developer portal whose personal-account access is unconfirmed; the BSP Open Finance framework (Circular 1122) has no confirmed live participant an individual can use. The owner can unblock the next step by trying UnionBank's portal and by sharing one exported statement per provider (values removed).

**Import** (Mint → Import; `StatementImport`, `MintStore.previewStatement` and `importStatement`): the player chooses an account and one CSV file through Android's document picker (no storage permission; strict UTF-8; at most 2 million characters and 5,000 rows, refused whole rather than cut). The reader handles quotes, doubled quotes, line breaks inside a field, comma, semicolon and tab, and a byte-order mark. Columns are guessed from the header and can be changed: date, description, one signed amount **or** money out and money in, an optional reference, and "charges are plus" for card statements. Dates: year first, day first or month first; detected when a day over 12 settles it, otherwise the player is told the file is ambiguous and checks the preview. Amounts are read exactly in the account's currency (`Money`): more decimals than the currency has, a different currency code, zero and absurd values are unreadable rows, never rounded or converted.

**Preview before anything is saved:** new rows, rows already imported (recognised by a fingerprint: account, day, signed amount, description, the file's own reference and a count for identical rows in one file), rows that **may match a record the player wrote** (same account, day, amount and direction; left out unless tapped; never merged or deleted) and unreadable rows with the reason. Then **Add n** saves the kept rows in one write as posted transactions of source `IMPORTED`, each with its batch (`csv:<millis>`), the file's name, its fingerprint and `unreviewed`. Importing the same file again, or retrying after an interruption, adds nothing (idempotent); an import cannot partly apply.

**After:** imported records show "imported" (and "unreviewed" until the batch is marked reviewed, section 9.1); each earlier import is listed and can be marked reviewed or **deleted on its own** (only its own records; a record the player wrote, a reconciliation and a bill stay, and a link to a removed record is just cleared). An import never pays: only reconciling and the weekly review do, and `reconcilePays` still counts only records the player wrote, so importing a file cannot unlock a payment. The data is sealed like the rest of Mint (fingerprints and file names are inside the sealed row).

**Not built:** a statement as the *other side* of a reconciliation session (5.8), PDF and spreadsheet (xlsx) statements, mapping a description to a category, transfers inferred between two imported accounts, foreign currency columns and rates, saved column mappings per account, an `ImportBatch` table of its own (a batch is derived from its records), provider connections and `FinancialProviderConnection`.

## 22B. As built: receive QR and recurring income

*Not run on a device (0.204.0, REP-039 first slice and an owner request, 15 November 2026). No schema change, no migration, `RewardRules.VERSION` unchanged (nothing pays differently).*

**Receive-money QR (4.2).** `FinancialAccount.receiveQr` holds one image as base64 PNG, at most `MintRules.QR_MAX_CHARS` (88,000 characters, about 64 KB; `MintRules.checkReceiveQr`), set with `MintStore.setReceiveQr`. It sits inside the account's existing sealed `mint_account` JSON row. The image is chosen with Android's Photo Picker through the `ImageChooser` interface (`App()` parameter `images`); `AndroidImagePicker` shrinks it to at most 600 px and 64 KB. The account options show a "Receive money" box (Replace, Remove; hidden when "hide values" is on); "Add QR code" is offered on Android only. The image is never uploaded and never read or decoded for its content. It is the one attachment built.

**Recurring income (8.3A).** `Bill.income` (JSON default false) marks a bill as money expected. "Received, record it" writes an income transaction through `settleAndRecord`, as "Paid, record it" writes an expense. Nothing is ever posted automatically. The Bills screen is "Bills and income", with a "Bill to pay / Income expected" choice and an income-category picker; the direction cannot be edited after creation.

**Not built:** the rest of section 22's list, plus other attachments (receipts, files), custom recurrence and spotting subscriptions.

## Shared Pokémon ecosystem contract

MINT's acquisition identity is **Ledger Research**. MINT owns eligibility for approved financial-organization actions; the shared Pokémon ecosystem owns reward settlement, canonical Egg/owned-Pokémon records, Pokédex, inventory and progression. MINT has an independent party referencing canonical owned Pokémon IDs. Financial amounts, balances, transaction details, merchants, account/provider identifiers and payment data are excluded from reward events. See `../FEATURES/POKEMON_ECOSYSTEM.md`.

---

## Testing reference

MINT persistence, transaction integrity, balance reconciliation, import deduplication, offline behavior and strict financial-data privacy boundaries must follow [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Use synthetic financial fixtures only.
