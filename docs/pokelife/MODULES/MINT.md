# MINT — Personal Finance Module

**Status:** Approved module specification  
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
| Household inventory and maintenance | BASE |
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

The Overview is the landing screen: a lead box, the overview of the records, the Quick actions box, then the party. The other destinations are reached from the header's Sections key. Quick capture is available throughout MINT.

| Destination | Purpose |
|---|---|
| Overview | Financial position, available money, upcoming obligations and recent activity (the landing screen) |
| Accounts | Banks, e-wallets, cash, cards, assets and liabilities |
| Activity | Transactions, transfers, search and filters |
| Plans | One place with two views behind a pill, **Budgets** (spending plans, category limits and savings targets) and **Bills and income** (recurring and one-time obligations and expected income) |
| Review | Reports (cash flow, spending patterns, historical comparisons) and reconciliation |
| Import | Statement files (where the platform can open a file) |
| Settings | Ledger Research, privacy, your data |

The three primary destinations are Accounts, Activity and Plans; Review, Import and Settings follow them in the same Sections key as secondary places (owner, 23 November 2026; `../FEATURES/DESIGN_SYSTEM.md` 6.7).

### 3.1 Navigation and quick actions

The Quick actions box holds Expense, Income and Transfer. The primary action is **Expense**: do not make the user navigate through multiple screens to record an ordinary purchase. Adding a bill, reconciling an account and importing a statement are reached from the Sections key.

### 3.2 Overview hierarchy

Show information in this order, adapting to available data:
1. Total net position, if the user has configured enough account and liability data for a meaningful calculation.
2. Available money, with its calculation basis visible.
3. Upcoming bills and obligations.
4. Budget progress.
5. Recent activity.
6. Optional trends or review prompts.

Avoid displaying every metric simultaneously. Empty states should offer the next useful action, not a tour of every feature.

### 3.2A Overview breakdown and quick hide

The Overview box on the main Mint screen has a **quick hide** button (an eye) in its title row that turns "hide values" (3.3) on and off in one tap, from the same setting as Settings, so every amount in Mint follows it at once. **Tapping the Overview box** opens its **breakdown** for the Overview's currency, so each figure can be traced to its parts: **net position** = assets less liabilities, listed account by account (name, group, balance) under Assets and Liabilities, with accounts hidden from the total (4.3B) shown apart as "Not in total" and left out of the sums; then **available money** = liquid accounts less bills due and **installments due** in the window chosen in 3.2B (14 days by default), with the counts shown, with the credit lines' limits, owed and available credit as information below it (a limit is in no total). Every amount goes through the same hide setting. The breakdown is read-only, derives everything from the records (`MintRules.breakdown`) and stores nothing.

### 3.2B Overview counts and the Mint widgets

**What the Overview counts** is set in Mint Settings, in an "Overview counts" box, and the screen, the breakdown (3.2A) and the widgets all read the same two choices (`MintSettings.overviewWindow`, `overviewPayLater`; `OverviewCounts` in mint-core), so no figure differs between them:
- **Bills and installments due** looks ahead: next 14 days (the default), next 30 days, the rest of this month, or until the end of next month. It sets the window of *available money* (liquid accounts less bills and installments due in it), of the "due" total and count, and of the upcoming list.
- **Pay later in net position** (`AccountType.PAY_LATER` only; a credit card always counts in full): **whole balance** (the default), **only what is due** (the unpaid installments of that account's plans that fall due in the window, never more than is owed), or **not counted**. The reason is that a long installment plan is mostly a future obligation, and the player may not want the whole of it taken off the overview today. It changes no record, no balance on the account's own screen and no installment; the account still shows what it owes, and in the breakdown its line shows what the Overview counts of it so the lines add up to the net position.

**Widgets (Android, Glance).** *Mint* shows available money, net position and the due total with its count; *Mint due* lists the next bills and installments (one line per bill or plan, soonest first, overdue marked) with their dates and amounts. Both show the chosen window in the title strip, open Mint on a tap and follow the privacy rules: with **Hide amounts** on every amount is dots and the counts and dates stay (3.3); with the **screen-lock gate** on (13.2) a widget cannot ask for the lock, so it shows no figure at all, only "Locked. Open Mint to see your money."; with no account yet it says to add one. A widget reads the sealed records read-only through the Keystore like a bill reminder does, pays nothing, writes nothing and shows no category, note, account name or card detail; a bill's name is shown (the player put it on their own home screen). **Open (owner decision, 22 November 2026):** a monthly credit bill's line currently draws the account's name, which this paragraph forbids; the widget must use a generic label instead.

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
- **Pay later** (labelled "Pay later"; buy now, pay later: SPayLater, Atome, Tabby-style and other BNPL lines): a credit **liability**, never a wallet (5.6A).
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
- Optional card colour (`FinancialAccount.card`): six quick swatches (Plain, Blue, Green, Gold, Violet, Slate, saved as style ids) or a custom `#RRGGBB` (Hue, Saturation and Brightness sliders or a hex field); an unknown value reads as plain. Cosmetic only: it changes no balance, rule or report.
- Optional **receive-money QR image** (`FinancialAccount.receiveQr`): a wallet's own "scan to send money to me" code, such as a GCash InstaPay QR; one image per account (PNG, JPEG or WebP; a whole wallet-app screenshot is fine so the QR stays sharp). It is picked with Android's Photo Picker (`ImageChooser`), stored as picked when it is at most 1,000,000 bytes (otherwise re-encoded as PNG, shrunk step by step until it fits; at most `MintRules.QR_MAX_CHARS` base64 characters) and kept as base64 inside the account's own sealed row: no table, no file outside the sealed store, so sealing, the lock gate, backup and delete cover it. The account's options show a "Receive money" box (Open, Share) only when a picture exists, the whole image at its own aspect ratio; with none, nothing is drawn and there is no switch. Add, Replace and Remove (Remove asks to confirm) are in the account's Edit form, Android only. Open shows it full screen (`QrFullScreen`: edge to edge, scrolls if taller than the screen, Close and Share on a slim bar, full brightness and the screen kept on while open through the window's own override, undone on close, no permission). Share hands the image to Android's share sheet as `receive-qr.png`, `.jpg` or `.webp` following the bytes (never the account's name; written to the cache `exports/` folder, older files removed each time). Open and Share are off while "hide values" is on. It is never uploaded, never read for its contents, and never appears in a reward event, notification, widget or log. A QR is a payment address, not a card number, CVV or password (last paragraph below), but the user is told it is private.

- Connection/source reference, if applicable.
- Status: active, archived or closed.
- Created/updated timestamps.

First run: the new-account form is titled "Where is this money?", the name field shows "GCash" as a placeholder, a "?" key opens one dialogue defining the nine account types, and saving the first account returns to the Mint overview, where Expense, Income and Transfer are the next actions.

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

A group is the account's optional `group` label (kept as text, no separate record). The Accounts list shows the accounts **under their group's heading**, in the order of the list, with the group's **subtotal** beside the heading (assets less liabilities, per currency, counting only the accounts that count in totals, 4.3B); accounts without a group sit under a plain "Other" heading, shown only when some account has a group. **Arrange** is a mode of the Accounts list (an "Arrange" button, "Done" to leave): every group heading and every account gets move up and move down, a group moves as a block, and an account moves within its group (to put it in another group, edit its Group). The order is the order of the accounts list, so it is saved and backed up with the accounts, changes no balance and pays nothing.

### 4.3B Hide from total

A per-account switch, **Hide from total** (`FinancialAccount.hideFromTotal`, default off), for an account the player does not want counted in the overview (money set aside, someone else's, a credit line they ignore). A hidden account still shows its own balance and records everywhere, can be reconciled and used in transfers, and is marked "not in total" on its row; it is left out of **net position**, **available money**, and the group subtotals. It changes no record and pays nothing.

### 4.3D Credit card starting balance and limit

For a **credit card** the opening balance field reads **Starting balance owed** (what is already owed when the card is added; it is the opening balance) and the card gets an optional **Credit limit** (`FinancialAccount.limitMinor`, in the account's currency, `0` is none, never negative, only for a credit card). With a limit the card's row and options show **used of limit** (the amount owed against the limit) and **left to spend** (limit less owed, never below zero; owing more than the limit shows 0 left and the amount over). It is information only: a limit is never an asset, never cash, never in net position or available money (4.5), and it changes no record and pays nothing. A limit can be edited at any time; the starting balance stays editable as the opening balance (4.2).

**Statement day and due day:** a credit card may also keep a **statement day** (the day of the month its statement closes) and a **due day** (the day of the month the payment is due), each `1` to `31`, `0` is none (`FinancialAccount.statementDay`, `dueDay`). A day later than the month has falls on the month's last day (31 in April is 30; 29 to 31 in a common February is 28), as for a bill (8.3). The card shows **Statement on the 15th** and **Due on the 5th** with how many days away the next one is ("today", "tomorrow", "in 4 days"), and its options show **current amount owed** (the card's balance, derived from the starting balance and its records, never typed twice), the limit, used of limit and left to spend. They are information only: no reminder, no bill and no transaction is made from them (a card payment is a transfer or a bill the player adds), and nothing is paid.

### 4.3E Interest a deposit account earns

A bank, e-wallet, digital-bank or other deposit account (not cash, never a liability) may keep an **interest schedule**: `earnSchedule` **Off**, **Every day** (Maya, MariBank and GoTyme credit daily) or **Every month**, a **yearly rate** (`earnRateBasisPoints`, 0 to 1000 percent), the **tax withheld** by the bank (`earnTaxBasisPoints`, 0 to 50 percent; 20 is the usual final tax) and **Post without review** (`earnAutoPost`, off by default). Switching the schedule on records the day (`earnFrom`); nothing is back-filled.

1. **Estimates are made automatically** when Mint opens (`MintStore.accrueInterest`), for complete days only. Each is one `INCOME` record in the **Interest** category, payee "Interest", with `interestThrough` set to the last day it covers. A daily schedule makes one record for all the days since the last one (so a week away is one record, not seven); a monthly one makes one record per calendar month that has ended (the first may be a part month; at most 12 are caught up).
2. **The figure** is the daily balance times the yearly rate over 365, summed over the days and rounded half up once, less the tax rounded half up. It uses posted records only, so an estimate still waiting never compounds. It is an estimate, not the bank's figure.
3. **Review.** Unless the account posts without review, the record is **pending**: it is not in the balance and is listed in an **Interest to confirm** box on the Overview with **Confirm**, **Adjust** (type what the bank really credited, then save) and **Skip**, and **Confirm all**. Skip keeps the record as voided, so the same days are not estimated again. A record of any status moves the next estimate on; deleting one makes the days estimable again.
4. **Reports.** A posted estimate is income (7.1), because the bank paid it. Nothing here pays a reward (16.2). Not a lender statement; reconcile (5.8) against the real balance as usual.

### 4.3C Open the wallet's own app

Optional `FinancialAccount.appPackage` (the Android package name of the account's own app, such as the e-wallet or bank). In the account's Edit form (Android only) the player picks the app from the apps installed on the phone (name only, listed on the phone, never sent anywhere; the manifest already holds `QUERY_ALL_PACKAGES` for the launcher, personal use), and the account's options then show **Open <app name>**, which starts that app and nothing else. Only the package name is saved; it is not a credential and carries no token. An app that is no longer installed hides the button.

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

Create, edit, archive, close and restore where supported. Archiving hides an account from default active views but preserves history. Closing records an effective date and prevents new ordinary transactions unless explicitly reopened. An account with an installment plan cannot be deleted or changed to a type that cannot hold plans. An archived or closed credit account still accepts repayments for its existing plans; it accepts no new purchases. Deletion is restricted when history or a plan exists; offer archive instead. Destructive deletion must explain dependent records and require confirmation.

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
- Optional receipt image (one PNG, at most about 1 MB), kept inside the transaction's sealed record and covered by selected exports, backups and transaction deletion.
- Status: pending, posted, reconciled, excluded or voided as appropriate.
- Provenance: manual, import, provider or adjustment.
- Optional import/source reference.
- Optional bill occurrence reference.
- Optional receipt attachment reference.
- IOU flag on an income: the money is owed back by the person in the payee field (5.8A).
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

**Transfer fee** (`Transaction.feeMinor`, default 0, transfers only, never negative): what the sending side charged on top of the amount (an InstaPay or bank-transfer fee). It is part of the same one record, so it can never be saved, edited, excluded, voided or deleted apart from its transfer. The **source falls by amount plus fee**, the **destination rises by the amount only**; the amount is still no income and no expense. The fee is **spending** (a fee, 7.1, in the transfer's category or uncategorised), is cash out of the liquid accounts for cash flow even between two liquid accounts, and counts as a fee in a credit account's activity when the card is the sender. A repayment to a credit account takes a fee the same way (the fee is not part of what the card receives, and plan allocation uses the amount only). The Activity form shows an optional **Fee** field on a transfer and the record's line shows "fee ₱15".

Transfers between different currencies preserve source amount, destination amount, rate, fee and rate provenance.

### 5.6 Credit cards and liabilities

A card purchase increases the card's outstanding liability and records the purchase expense once. Paying the card reduces the associated bank/cash asset and the card liability; it is a transfer/settlement, not a second expense. Fees and interest are separate expense transactions. Card limit, outstanding balance, statement balance and payment due amount must not be conflated.

### 5.6A Credit lines, pay later and installment plans

A **Pay later** account (`AccountType.PAY_LATER`, such as SPayLater) is a credit line like a credit card: a liability whose balance is **what is owed** (outstanding), never cash and never liquid. A credit card and a pay-later account both have the optional credit limit, statement day and due day of 4.3D; **available credit** is limit less outstanding (never below 0) and, like every limit, is in no total. Further services (Atome, a store card, another card) are just more accounts of these two types, so nothing here is specific to one provider.

**How each event is recorded (one record each, never two for one event)**
- **Purchase:** one `EXPENSE` on the credit account at the **full price**, on the purchase day, in its category; the outstanding rises by the price. It counts in spending once, on the purchase day, whether or not it is paid in installments (5.6, invariant 4).
- **Repayment:** a `TRANSFER` from a source account (a wallet, bank or cash account the player names: the **payment source**) to the credit account. The source's cash falls, the outstanding falls, nothing is spent and no second expense exists. It may be any amount, a **partial** payment or an **early** one.
- **Fee, interest, late fee:** a separate `FEE` record on the credit account (`Transaction.feeKind` `FEE`, `INTEREST` or `LATE`), which raises the outstanding and counts in spending as a fee or interest, never as the purchase. A fee is recorded **when its installment is billed** (the player taps "Bill it" on the due installment) **or when a payment reaches that installment**, whichever is first, dated that day (not all fees up front). A late fee is recorded when the player adds it.
- **Refund:** a `REFUND` on the credit account (linked to the purchase), which lowers the outstanding and lowers spending; on a plan it cancels the **unpaid installments from the last one backwards** (a cancelled installment owes no principal and no fee).

**Installment plan** (`InstallmentPlan`, saved as the `plans` list in the sealed `mint_state` table). A plan belongs to one credit account and holds: the purchase record, the price, the first and every later due day, and the generated **schedule** of installments (`no`, due day, principal, fee). Generating the schedule is automatic from the price, the number of installments (1 to 36), the interval (every week, every 2 weeks, every month) and the first due day: the principal is split evenly (the leftover minor units go one each to the first installments, so the sum is exact); the fee is none, a fixed amount per installment, a total spread evenly, or a **rate per period on the principal still unpaid** (basis points, rounded half up); a monthly due day that a month lacks falls on its last day. The purchase/order name can be changed later. Before any installment has a payment or recorded fee, the player can edit the plan's principal, count, interval, first due day and fee rule; the purchase record changes with its plan. After that, a payment or recorded fee locks only the installment it reached; other unpaid installments can still have their principal, due day and fee edited without rewriting history. Everything else is **derived, not stored**: what each installment still owes is its principal plus its fee plus its late fees, less repayments. A repayment to an account with unpaid plans is allocated **installment by installment** in due-date order, then by plan creation order (5.6C), so it settles the earliest monthly bill and never one purchase's whole schedule before another purchase's earlier month. Any amount beyond all scheduled balances is an ordinary account repayment. The states are Upcoming, Due, Overdue, Partly paid, Paid and Cancelled. Voiding or deleting a repayment therefore reopens the installment by itself. A plan may be deleted: its records stay (unlinked), and its purchase record cannot be deleted while the plan exists.

**What it does to the reports (one rule each, tested)**
- **Spending** (7.1) = posted `EXPENSE` and `FEE` less `REFUND`, on any account: the purchase once, plus fees, interest and late fees; a repayment never counts. An IOU income (pending or posted) lowers spending in its category like a refund (5.8A).
- **Cash flow** is what really moves through the **liquid** accounts (wallets, banks, cash): income in, expenses and fees paid from them out, and **a repayment to a credit account is cash out** (the cash really leaves); a purchase on credit is not cash flow until it is repaid; a transfer between two liquid accounts is neither.
- **Net position** (assets less liabilities): a purchase on credit lowers it at once by the price (the liability), a fee lowers it by the fee, a repayment changes it by nothing (cash and liability fall together), a refund raises it.
- **Available money** (3.2, 4.5) also subtracts the installments due within the window (14 days by default, 3.2B; overdue ones included) with the unpaid bills.
- **Credit activity** of an account over a period: purchases, fees, interest, late fees, refunds and repayments, so the statement can be read without mixing the limit, the outstanding, the statement balance and the payment due (5.6).

**Installments already owed.** When the player starts tracking a pay-later or card account that already has installments running (for example the next three months of SPayLater), they add them as an **existing schedule**: the player enters, for each upcoming installment, its due day, the amount still owed (principal) and optionally a fee on top. It makes a plan **with no purchase record** (`InstallmentPlan.purchaseTxId` is null; the price is the sum of the amounts) because the purchase happened before tracking: it is **not spending** and nothing is recorded as an expense. What is owed is added to the account's **starting balance owed** (the opening balance, 4.2) so the outstanding equals what the schedule asks; the form says so, and offers to leave the opening balance alone for a player who already typed the whole owed amount there. From then on the plan behaves like any other: Pay (a transfer from the payment source), Bill it, Late fee, Edit, Refund and Delete work the same, and a fee is recorded when billed or reached by a payment (so a fee on top is not in the starting balance). Deleting such a plan removes only the schedule; the opening balance stays as it is.

**Installment reminders:** one reminder per open monthly bill, not per installment (5.6C item 7).


**Account statement and due days** are informational and do not schedule installment reminders. Installment reminders use each plan's due dates and require the global Bill reminders setting, which is off by default. Early repayment does not recalculate already scheduled rate-based fees; Mint's schedule is not a lender-provided early-settlement quote. A CSV import is a one-time manual import: it never connects to a provider or matches rows to plans automatically.

### 5.6C One monthly bill per pay-later or card account

The principle: **an account has one payable a month**, however many purchases sit behind it. Six purchases on SPayLater are one December bill, not six; the player pays the account, and the purchases are detail. Purchase-level plans (5.6A) are unchanged: they stay the record of each purchase and are one tap away.

1. **A monthly bill is derived, never stored** (`MonthlyBills`). For one credit account, the bill of a calendar month is every installment of every plan (principal, recorded or unrecorded fee, late fees) that falls due in that month, with what each line has been paid. Its due day is the earliest due day among the lines still owed; its state is Overdue, Due, Partly paid, Upcoming or Paid by its lines; a month with nothing left owed has no open bill (paid months stay in `bills`, the history). Cancelled installments are not lines. Voiding a payment or editing a plan reopens or reshapes the bill by itself, because nothing about it is saved.
2. **Record a purchase once.** The purchase form asks for the name, price, category and count; **the first due day is not typed**: for a monthly plan on an account with a due day, the purchase is billed on the first statement on or after the purchase day (the purchase day itself counts; with no statement day, the purchase day) and falls due on the next due day after that statement; without a due day, or for a weekly plan, it is one step after the purchase as before. The player can still pick a first due day (it is then kept). So the installments land on the account's day by themselves, and the two day-of-month facts live **once, on the account** (4.3D).
3. **Future obligations first.** In the account's Credit line box the **Monthly bills** list comes before the Purchases: the next three open months (the rest behind "Later months"), each a month, its due day, how many purchases, and what is left. A bill opens to its lines (purchase, "2 of 3", what is left; a line opens that purchase's plan to inspect or edit it) and to **Pay**. The Overview's due breakdown, the Bills screen's credit box and the due count read the same bills: one line per account and month, not one per purchase.
4. **Payments belong to the account and are allocated to the bill.** Paying a bill, or recording a repayment to the account from Activity, is the same one `TRANSFER` from a payment source to the account (5.6A). It is allocated to the account's unpaid installments one by one in due-date order, then plan creation order, so the oldest bill is settled first and a partial payment leaves the rest of that bill owed. **Paying a chosen bill** puts that month's lines first and any extra goes to the earliest remaining bills; extra beyond every scheduled balance is an ordinary repayment. The allocation rows name the installment (`PlanPaymentAllocation.no`); a row without one (a payment made from a single plan's own page) is applied to that plan's earliest installments first. A payment can be voided; the bill reopens by itself.
5. **Purchase-level detail stays.** Every plan keeps its schedule, Edit plan, Rename, fee, late fee, refund, delete and its own Pay (a payment aimed at one purchase). Nothing in 5.6A is removed.
6. **Low-maintenance manual entry.** An "Already owed" schedule defaults its months to the account's due day; "Use the 5th for every bill" (shown only while some unpaid monthly installment is off the account's due day) moves those unpaid installments onto it in one tap, leaving paid, billed or fined installments alone, so a player never edits purchases one by one to make one bill. A plan that would end up out of due order is left as it is.
7. **Reminders: one per bill.** With Bill reminders on (12) an open monthly bill asks for **one** reminder on its due day less the lead (never one per purchase), uses the same words and name setting as a bill (the account's name, never an amount), stops when the bill is settled and never repeats for an overdue one.

Nothing here records spending or moves money by itself: the purchase is still one `EXPENSE` at the full price (5.6A), the cash flow, net position and spending rules are those of 5.6A, and the account's statement and due days are information that drives the default first due day only.

### 5.6B Statement tracking and estimated interest

For a credit card or pay-later account, the player may record the lender's statement balance, minimum payment and due date as facts from the statement. These values do not create a transaction or reminder; payments are still recorded by the player. The player may optionally enter an annual rate and a grace period for automatic estimated interest. When a statement is recorded, Mint calculates simple daily interest on the posted liability balance for each eligible day after the prior due date plus the grace period, using `APR / 365`; it rounds the period total half up to the minor currency unit and posts one estimated `INTEREST` record. A unique account-and-statement-date key prevents duplicate accrual. It is an estimate, not a lender calculation, statement or early-settlement quote. The grace period is the number of days after the previous statement's due date before estimated interest starts. A zero rate accrues nothing.

### 5.6D The card month, step by step

The account's Credit line box has a "?" key that opens **How a card works**, and a **Pay card** button (**Pay balance** on a pay-later or other credit account) while something is owed. The steps, each already defined above:

1. **Spend:** an Expense on the card. It raises **Owed now** and counts as spending once, on the day (5.6, 5.6A).
2. **Statement:** the lender's statement is entered under Statements (5.6B). It is a note of what was billed and what is due; it changes no balance and makes no record. The box shows the latest statement's balance, minimum and pay-by day under **Owed now**, so the two are never mistaken for each other.
3. **Pay:** Pay card opens Activity as a **Transfer to the card** (the card already chosen); with unpaid installment plans it fills the earliest due installments first (5.6C). Owed now and the paying account fall together; it is not spending, and the form says so. An optional transfer fee (5.5) can be added.
4. **Fees and interest:** a late fee, annual fee or interest is a **Fee or interest** record on the card (5.6A, 5.6B). It raises Owed now and counts as spending.
5. **Limit:** the limit and available credit are information only (4.3D).

### 5.7 Splits, duplicates and edits

Support split transactions where one transaction is allocated across multiple categories. At least two positive parts must add up exactly to the transaction amount in its original currency; parts use minor units, so no rounding is needed. Category budgets and reports count each part once. Transfers and adjustments cannot be split.

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

### 5.8A IOUs: income someone owes back

A lightweight flag on one income transaction, not a ledger, not a screen and not a module. It answers one question: money someone owes the player back, where does it sit?

- **What it is.** A `Transaction` of type `INCOME` with `iou = true`. The person who owes is its `payee`; its category is a *spending* category, the one it takes spending off, like a refund. Only an income can be an IOU, and it cannot be split.
- **Entering it (revised 23 November 2026).** Entering an income already means the money arrived, so an IOU is always saved `POSTED`: the Income form has the toggle "Paid back by someone (IOU)" and "Who paid it back", and no "they paid me" toggle. The IOU stays a note on the record (the list shows "IOU") so it still takes its amount off its category. An older save may hold a `PENDING` IOU: it keeps its "Mark received" action, and a posted one offers the ordinary "Mark pending".
- **What it does to the books.** It counts as **no income** (`MintRules.earned` leaves it out). **Spending, budgets, category totals and the review's spending fall by its amount in its category from the moment it is recorded, owed or received** (`countsForSpending`, direction -1 like a `REFUND`); excluded and voided records do not count. The account balance rises only when it is `POSTED`.
- **Rewards.** None: Ledger Research pays an expense or a transfer, and an IOU is income.
- **Editing.** An edit keeps `iou` as it was; a plain income cannot be turned into an IOU by editing.
- **What it does not do.** No who-owes-whom balance, no settle-up, no splitting one bill by share between several people (REP-073), no interest or due dates.
- **Private like the rest of Mint:** the flag rides in the transaction's own sealed row, so there is no table, no schema step and no migration, and an older save without it loads unchanged. No `MintEvent` and no figure leaves Mint for an IOU. The earlier Shared bills ledger is removed.

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

A budget stores the planned amount and period; both can be edited afterwards, and a change that would make a second budget for the same category, currency and period is refused. Actual spending is derived from eligible posted transactions (an IOU income takes its amount off its category from the moment it is recorded, owed or received, 5.8A). Transfers are excluded. Refund treatment is explicit and consistent (normally offsets the originating category's spending). Pending transactions may be shown separately or included by user preference, but must never be silently mixed with posted actuals.

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

Savings targets are planning records, not separate bank accounts. A target links to one or more explicitly selected asset accounts and defines a target amount/date; its progress is derived from their current posted balances. An account can count toward only one non-archived target, preventing double-counting. Savings targets never create artificial deposits or transfer records.

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

A settled occurrence (paid or skipped) stays reachable for 60 days after its due day (`MintRules.settled`, the Bills section's "Settled lately"): it can be settled again with another amount, day or status, which replaces the earlier settlement, or taken back to unsettled.

### 8.3 Recurrence

Support weekly, monthly, every six months and yearly schedules; custom schedules follow only where the recurrence engine can handle them reliably. Six-month dates are counted from the first due date, clamping a missing day to month end without drifting. Define behavior for end-of-month dates, leap years, time-zone changes, paused recurrence and edited future occurrences. Never rewrite historical bill occurrences when a future schedule changes.

### 8.3A Recurring income and expenses (REP-039)

A salary, allowance or subscription that repeats is a **recurring entry**, and it uses the same schedule, occurrences and lifecycle as a bill (8.1 to 8.3): a `Bill` has a direction, `income = false` for something to pay and `income = true` for money expected in. Nothing new is stored and nothing is posted by itself:
- **Due item you confirm.** On the due day an occurrence is Due (and Overdue after), exactly as for a bill. "Paid, record it" (bill) or "Received, record it" (income) asks for the account and the real amount (the expected amount is the default), then writes the transaction (an expense for a bill, an income for a recurring income) and links it both ways in one save. The app never posts a transaction on its own, including when it was closed on the due day, because a wrong amount must never post silently.
- **Skip** and **Mark settled without a transaction** work as for a bill; skipping never deletes the schedule. A partly received amount keeps the remainder due (8.2).
- An income's category is an income category and its transaction is an income, so it counts as money received (7.1) and never as a refund. The direction is fixed when the entry is created and cannot be edited afterwards, as the schedule cannot (8.3).
- Weekly, monthly, every six months and yearly; month-end and leap-day follow the bill rule (a 31st falls on the last day of a shorter month, 29 February on 28 February in a common year). Custom schedules are not built.
- A reminder (section 12) says only that something is coming up, for an income as for a bill, and never an amount.

### 8.3B Paydays: twice a month, on working days, estimated

A recurring income (8.3A) or a bill may repeat **twice a month** (`Frequency.SEMI_MONTHLY`, "Twice a month") on two days of the month (`Bill.days`, up to four; empty reads as the **15th and 30th**); a day the month lacks is its last day (the 30th is 28 or 29 February), counted from the first due day so nothing drifts, and the dates of the first month before the first due day are not part of the schedule.
- **Working day** (`Bill.workday`): each date moves to the **nearest working day on or before it**: not a Saturday or Sunday and not a holiday that counts (the 15th on a Sunday is paid on the Friday; the 30th of November 2026, Bonifacio Day on a Monday, is paid on Friday the 27th). The scheduled day stays the occurrence's **key** (`Occurrence.dueDay`: a settlement is saved under it, so changing a holiday never orphans one) and the day it is **really due** is `Occurrence.payDay`, which sets Upcoming, Due and Overdue, the order of the list and a reminder's day.
- **Holidays:** the Philippine holidays worked out from the year (`PhHolidays`: the fixed regular holidays, the special non-working days declared on the same date most years, National Heroes Day on the last Monday of August, Maundy Thursday, Good Friday and Black Saturday from the date of Easter) count by default; in Mint settings the player can turn the built-in list off, stop counting any one built-in day, and **add days off of their own** (Eid, a proclaimed day). Changing the list moves the next paydays at once and rewrites nothing that was settled. The built-in list is a starting point, not an official calendar: moving holidays and one-off proclamations are the player's to add.
- **Estimated, not final** (`Bill.estimate`): the amount is shown as **about** it, and "Received, record it" always asks for the real amount, with the estimate as its default (8.3A). Nothing is posted by itself; the income counts in spending-side reports, cash flow and net position only when it is recorded, at the real amount and on the day it really arrived (the player may pick a day before `payDay`'s date, never a future one).
- Not built: skipping a payday that falls in a gap between two paydays by the employer's calendar, other country calendars, a range of amounts.

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

MINT offers optional device-authentication gating: the phone's own screen lock (`KeyguardManager` confirm-credential, `DeviceGate`, `AndroidDeviceGate`) before the Mint screens, asked again after the app or Mint was left for 30 seconds. Its switch is Lock Mint in Me, Settings, Privacy and access, next to the same switch for Care, Heal, Base and Link; it writes Mint's own `gate` setting and has no copy in Mint settings. The gate is a door only: it has no key or password of its own, so it cannot lock anyone out or make the data unrecoverable, it is unavailable without a screen lock and on desktop and web, and it does not guard the files (the sealing does, `../ARCHITECTURE/DATA_AND_MIGRATIONS.md` 10.4.1) or Settings → Backups. Recovery and backup are specified by shared architecture. Not built: a gate on the Pokémon side of Mint's party.


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

The receive-money QR image (4.2) is kept inside the account's sealed row. A transaction may also carry one optional PNG receipt image, up to about 1 MB, inside its sealed transaction record. It is available through the Android image picker, hidden when MINT values are hidden, and included only when the user selects transaction data for export or backup; deleting the transaction deletes its receipt. No image URI or sensitive filename is retained. Additional files and multiple receipts per transaction remain unbuilt.

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

This identity is distinct from HEAL's Field Research and BASE's Habitat Discovery.

### 16.2 Eligible activity families

Eligible activity events may include:
- Completing an optional financial review.
- Reconciling an account.
- Reviewing/categorizing a meaningful batch of imported transactions.
- Creating or completing a budget review milestone.
- Reviewing upcoming bills as part of a periodic review.
- Completing a defined periodic financial organization milestone.

**Logging a purchase, a payment or a transfer is reward-bearing.** The event is `record_logged` (`MintKind.LOGGED`) and it **never depends on an amount**: the same flat reward whether the record is ₱5 or ₱50,000, and it carries no figure, account, category or payee (16.3). Only a record the player types or taps in today (dated today, manual; not an import, an interest estimate, a fee Mint made itself, an adjustment or a backdated entry) of type **Expense** (a purchase, including a bill or card installment paid) or **Transfer** (a payment to a card or loan, or money moved between accounts) pays; income (an IOU included), refunds, fees and balance adjustments do not. **There is no daily limit** (a frequent action pays about like a Plan focus egg or a Care visitor, and farming is the player's own responsibility); each record id pays once and adds a point to Ledger Research's Egg meter. The amounts are in `docs/economy.md`. Opening MINT, viewing balances, receiving income, changing an account balance, connecting a bank, increasing savings, empty records and repeated edits are still not reward-bearing; a micro-entry is allowed to pay, because the player chose that trade.


Do not reward creating empty records, repeated edits, repeatedly opening the same review, or artificial micro-actions. Define eligibility thresholds and caps in the shared economy/progression ledger, not in UI code.

### 16.2A Outbreaks: the rare actions pay a choice and better shiny odds

A **reconciled account** or a **finished weekly review** also opens an **outbreak** (`Outbreak`, saved in `AppData.outbreaks`, at most 5 waiting): three different Pokémon of the Mint Region to choose from, shown on the Overview as "Outbreak: choose one". Tapping **Catch** under one **catches it directly, with no egg**: it joins Mint's party (else the PC), leads Mint if there is no lead, pays the usual new-species candies, and uses the outbreak up. The options come from better rarities than an egg gives, species not caught yet are likelier, and the caught Pokémon has a fixed number of extra **shiny rolls** set by the action — two for a reconciled account, three for a weekly review — never raised by a streak or by how many weeks in a row a review was finished (`RewardEngine.mintOutbreak`; the figures are in `docs/economy.md`). That keeps the event free of any figure and the reward free of attendance: a missed or reset run of reviews takes nothing away. It does not use Plan's Shiny Hunter perks. The existing meter, candies and friendship from these moments are unchanged.


### 16.3 Minimal progression event contract

MINT may emit only approved abstract events, for example:
- `financial_review_completed`
- `account_reconciled`
- `budget_review_completed`
- `bill_review_completed`
- `record_logged` (a logged purchase, payment or transfer, 16.2)

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
- MINT does not create its own currency, Bag or duplicate Pokémon records. It has an independent party referencing canonical owned Pokémon IDs; a Pokémon lives in one place only (`../FEATURES/POKEMON_ECOSYSTEM.md`).
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
- Use the app's Pokémon DP font (regular weight) for financial values, long text, forms and notes; the app has no system typeface (DESIGN_SYSTEM.md 5.5).
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

Status and the open work list are in `../IMPLEMENTATION_BACKLOG.md`. Order: P0 the foundation, accounts, transactions, budgets and bills; P1 the Overview, reports, financial review, CSV/statement import, reconciliation, complete local export and the Ledger Research event contract; P2 provider integrations, provider by provider.

Provider integrations must not block complete manual finance functionality, and an official API or aggregation adapter comes only after documented access, consent, security and coverage validation; notification parsing only after policy and technical review.


## 19. Acceptance criteria

> Boxes checked against the code in 0.236.0 (23 November 2026): a tick means built and found in the code; an unticked box says what is missing.

### Accounts and balances
- [x] Users can create, edit, group, archive and close supported account types.
- [ ] Opening balances and timestamped snapshots are preserved. — partial: opening balances keep their amount and day, but 4.4's BalanceSnapshot (source, batch, observed time) has no type or screen
- [x] Assets, liabilities and net position are calculated correctly.
- [x] Credit limits are never counted as assets or cash.
- [ ] Every external balance displays source and freshness. — not built: no external or provider balance exists to show a source or freshness (REP-039)
- [ ] Failed sync cannot erase or overwrite a valid local snapshot. — not built: no provider sync exists to fail (REP-039)

### Transactions
- [x] Expense, income, transfer, refund, fee and adjustment are supported.
- [x] Ordinary expense capture is fast and does not require optional fields.
- [x] Transfers are atomic and excluded from income/expense totals.
- [x] Credit-card purchases and settlements are counted correctly.
- [x] Split allocations reconcile to the parent amount.
- [x] Duplicate imports are detected without silently deleting ambiguous records.
- [x] Search, filters, editing, exclusion, voiding and deletion behave consistently.
- [x] Monetary calculations use exact arithmetic.

### Budgets and bills
- [x] Budget actuals derive only from eligible transactions.
- [x] Transfers do not inflate spending.
- [x] Refund treatment is consistent.
- [ ] Rollover is explicit and configurable. — not built: a budget has no rollover choice (7.3, REP-039)
- [x] Bill recurrence handles month-end and leap-year cases.
- [x] Paid status does not fabricate a transaction.
- [x] Payment links do not create double-counted expenses.
- [x] Bill reminders use the shared feature and never execute payments.

### Integrations and offline
- [x] MINT works fully without a bank connection or internet.
- [x] Provider support is claimed only after end-to-end verification.
- [x] No password, PIN, CVV or OTP scraping/storage exists.
- [x] Imported records retain provenance and timestamps.
- [ ] Failed integrations preserve local records. — partial: a failed statement import saves nothing and a retry adds nothing, but no provider integration exists (REP-039)
- [ ] Offline exchange rates are timestamped and never presented as current. — not built: there are no exchange rates or converted balances at all (11, REP-039)
- [x] Import/reconciliation can recover after interruption.

### Privacy and security
- [ ] Hide-values mode covers all MINT surfaces and accessibility output. — partial: every displayed figure and the widgets show dots, but opening Edit pre-fills the plain amount (Mint.kt:1030)
- [x] Sensitive data is excluded from logs, analytics and game services.
- [x] Notifications are generic by default.
- [ ] Provider disconnection does not delete manual records. — not built: there is no provider connection to disconnect (REP-039)
- [x] Export, backup, restore and erase pass tests.
- [x] Optional access protection follows shared Android security architecture.

### Pokémon progression
- [x] Ledger Research is MINT's only acquisition identity.
- [x] Only approved administrative activities emit reward events.
- [x] Events contain no financial amounts, provider names or private records.
- [x] Shared reward ledger and collection remain authoritative.
- [x] Retries cannot duplicate rewards.
- [x] Disabling progression leaves MINT fully functional.

### Presentation
- [x] Shared PMD dialogue-box system is used without a separate MINT design language.
- [x] Dense activity records remain readable and easy to scan.
- [x] Light/dark themes and larger font scales work.
- [x] Privacy mode is consistent.
- [ ] Empty, loading, offline, stale and error states are clear. — partial: empty and refusal states exist; loading, offline, stale and connection states do not (REP-039)
- [x] No redundant cards, captions or decorative Pokémon compete with finance tasks.

## 20. Reuse policy

Reuse the shared Pokémon collection, reward ledger and economy interfaces, navigation and module registration, database, backup and migration foundations, design-system components and tokens, and the shared reminder scheduler. GEAR's generic currency formatting or conversion primitives only if they preserve MINT's source/rate provenance and do not take ownership of financial records.

Avoid: adding financial fields to a legacy global save object; storing MINT records in PLAN, WORLD or GEAR repositories; another Pokémon inventory or currency; bank scraping through accessibility automation or credential capture. Keep progression event delivery isolated from financial repositories, imports provenance-aware, backup/export/erase covering financial data, and provider adapters explicit, permission-scoped and independently revocable.

## 21. Documentation ownership

Product-wide MINT purpose and constraints: `../PRODUCT_BIBLE.md`. Visual language: `../FEATURES/DESIGN_SYSTEM.md`. Shared reminders, providers, permissions, secure storage and privacy: `../ARCHITECTURE/INTEGRATIONS_AND_PRIVACY.md`. Persistence, migration, backup, restore and erase: `../ARCHITECTURE/DATA_AND_MIGRATIONS.md`. Delivery status: `../IMPLEMENTATION_BACKLOG.md`. This file owns MINT workflows, domain, integrations, privacy adaptation, progression and acceptance; do not duplicate the shared contracts.

## 22. Implementation notes

What the code does today where it adds to the sections above. Not run on a device.

**Screens and settings.** The menu is Overview on top, Accounts, Activity, Budgets, Bills (titled "Bills and income"), Review (this month's money in and out, spending by category, the reconcile flow of 5.8, the weekly review) and Settings (Ledger Research, hide amounts, default currency, categories added, renamed, put away and deleted, also from the entry form, export and erase by kind).

**Money.** `Money` keeps integer minor units with a per-currency scale (JPY, KRW, VND have none) and parses typed amounts (`1,250.50`, `1.250,50`, `12,5`) without floating point or rounding (more decimals than the currency has is refused). Amounts are shown as `₱1,250.50` for the peso (the app's Pokémon DP font carries the sign) and as `USD 1,250.50`, the code, for every other currency. Activity edits a record's date, account and category; while an installment purchase is linked, its amount and account stay locked and its plan is managed from the schedule. A transaction has one currency, equal to its account's; a transfer between different currencies is **not supported yet** (5.5), and no amount is ever added across currencies. A balance is derived (`opening + posted records`), pending is shown apart, a liability's balance is what is owed, and an adjustment moves the displayed balance in the direction it says.

**Ledger Research (section 16).** The amounts are in `docs/economy.md` (rules version `RewardRules.VERSION`).
- On by default (as is every acquisition method), off in Settings without touching a record (16.4). Three things pay: an **account reconciled** (the user compares a statement figure with Mint's; a difference is saved only as an explicit adjustment record they accept; the account must have real transactions), a **finished weekly review** (there must be an open account and a transaction) and a **record logged** (16.2). Entering income, a balance, a budget, a bill, an empty account or a repeat of the first two never pays. The world checks the caps again (`GameStore.MINT_CAPS`).
- The event is exactly `MintEvent`: a stable `id` (`mint:<kind>:<record id>`), `module` ("mint"), an opaque `ref`, the broad `kind` (`account_reconciled`, `financial_review_completed` or `record_logged`) and the time. `MintStoreTest.reconcilingPaysOnceAndTheEventCarriesNothingButTheContract` and `MintTablesTest.noAmountOrNameOfMintEverReachesTheSharedTables` prove no amount, currency, account, merchant, category or note reaches the event, the shared save, the reward log, the ledger or the Pokémon tables. The game remembers paid ids (`AppData.mintPaid`, the newest 1000) and the record keeps `paid`.
- A full Egg meter hatches an egg into Mint's party from the **Mint Region** (`tools/species/regions/mint.txt`). Mint's party perks (Candy finder, Friendship gain) change only what Ledger Research pays.
- **First-run tutorial** (`POKEMON_ECOSYSTEM.md` 10.2.1): a 5-second tally gives one Mint Region Pokémon into Mint's party, leading it, once per save; it creates no record, event, meter point or cap use.

**Data (section 15).** The `mint_*` tables are listed in `../ARCHITECTURE/DATA_AND_MIGRATIONS.md` 7.4 (each row `key, position, day, json`); `mint_state` holds the settings and the installment `plans`, as JSON in the same sealed row. `MintTables` reads, exports or erases one family alone. Mint is in the daily backup and the backup bundle (`BackupBundle.KNOWN`); restoring a backup made before Mint existed leaves Mint alone. **Every row and every copy of Mint is sealed** and Mint fails closed without the Keystore key (`DATA_AND_MIGRATIONS.md` 10.4.1 owns the cipher, keys, failure modes and the passphrase-sealed portable backup). The cost: **a backup of Mint opens only on a phone that has the key**, or elsewhere with the passphrase of a portable backup. Mint's own **Export** (Settings) is the way to move records between phones: it is a readable file and says so.

**Bill reminders (section 12).** Off until turned on (Settings), then one reminder per occurrence, `0` to `7` days before the due day at a chosen hour, through the shared scheduler (`MintReminders`, `MintReminderAdapter`, `BillReminders`): quiet hours and focus sessions hold it, an overdue bill is not repeated, a settled, paused, deleted or switched-off bill stops asking, the only button is "Tomorrow" (never "Paid"), and the request holds only the bill's opaque id and its due day. The lock screen always says "A bill is coming up"; the bill's name shows only after unlocking and only if the user allowed it; never an amount. Installments and monthly bills are asked for the same way (`MintReminders.billRef` and `parseBillRef`; one ref names an account and a month, not an installment; 5.6C item 7).

**Account edit.** An account's name, type (within its side), provider, group, last digits and opening balance can be edited, and its currency while it has no records.

**Plans and entry points.** `Installments.allowedFor` says which accounts hold plans (credit card and pay later). `MintStore` has `addPlanPurchase`, `addExistingPlan` (a plan with `purchaseTxId` null; `Installments.checkExisting` validates its rows), `payPlan`, `billInstallmentFee`, `addLateFee`, `refundPlan`, `updateInstallment`, `deletePlan`, `planView(s)` and `creditActivity`. Beyond the account screen, the Activity add form has a "Pay in installments" toggle on an expense to a credit account and a transfer to a credit account shows "Pay for" chips (No plan and each unpaid plan); transfers into a liability are titled "Repayment". The Bills and income list has a "Credit bills due" box (due within 30 days or overdue; a row opens the month's purchases with Pay). A repayment above the plan balances reduces the account balance by the full transfer, with only the allocated amount applied to schedules; refusals (excluding, voiding, deleting or editing the amount or account of a linked purchase; deleting an account that holds plans) are shown; deleting a plan says its records stay. "Add plan" leads to New purchase or Already owed.

**Not built.** Bulk categorisation; reconciliation sessions with imported statements as the other side (5.8); rollover (7.3); renaming a group (a group is only the account's text label, 4.3); reminders from statement or due days; foreign currency, exchange rates and multi-currency plans; per-installment grace periods and lender early-settlement quotes; custom recurrence, spotting subscriptions, other countries' holiday calendars and a range of amounts for an estimate; additional attachment types and multiple receipts per transaction (13.4); provider connections and notification parsing (10); the `budget_review_completed` and `bill_review_completed` events; charts.

**Open for the owner** (not changed): Exclude and Void have the same accounting effect (neither counts in a balance, budget or report; 5.4 reads as if an excluded record should still move the balance); Archive and Close have the same effect (4.6 gives closing an effective date, which is not kept).

## 22A. Statement import and provider status

**Classification** (`INTEGRATIONS_AND_PRIVACY.md` section 2): the statement file is a *user-assisted import*. GCash, Maya, MariBank, GoTyme and UnionBank are *unsupported or unverified*: the due diligence of section 7.1 of that file (a documented personal-account API, independent developer access in the Philippines, production approval for an individual app, scopes and terms) **has not been done**, so none is offered, and the page says "no verified way to read a personal account". Reading payment notifications is not built (separate privacy review first). Nothing logs in, scrapes, uses Accessibility, or asks for a password, PIN, CVV or code. Desk research found no documented route for an individual developer's app to read a user's own transactions from GCash, Maya, GoTyme or MariBank; UnionBank has a business-oriented developer portal whose personal-account access is unconfirmed; the BSP Open Finance framework (Circular 1122) has no confirmed live participant an individual can use.

**Import** (Mint → Import; `StatementImport`, `MintStore.previewStatement` and `importStatement`): the player chooses an account and one CSV file through Android's document picker (no storage permission; strict UTF-8; at most 2 million characters and 5,000 rows, refused whole rather than cut). The reader handles quotes, doubled quotes, line breaks inside a field, comma, semicolon and tab, and a byte-order mark. Columns are guessed from the header and can be changed: date, description, one signed amount **or** money out and money in, an optional reference, and "charges are plus" for card statements. Dates: year first, day first or month first; detected when a day over 12 settles it, otherwise the player is told the file is ambiguous and checks the preview. Amounts are read exactly in the account's currency (`Money`): more decimals than the currency has, a different currency code, zero and absurd values are unreadable rows, never rounded or converted.

**Preview before anything is saved:** new rows, rows already imported (recognised by a fingerprint: account, day, signed amount, description, the file's own reference and a count for identical rows in one file), rows that **may match a record the player wrote** (same account, day, amount and direction; left out unless tapped; never merged or deleted) and unreadable rows with the reason. Then **Add n** saves the kept rows in one write as posted transactions of source `IMPORTED`, each with its batch (`csv:<millis>`), the file's name, its fingerprint and `unreviewed`. Importing the same file again, or retrying after an interruption, adds nothing (idempotent); an import cannot partly apply.

**After:** imported records show "imported" (and "unreviewed" until the batch is marked reviewed, section 9.1); each earlier import is listed and can be marked reviewed or **deleted on its own** (only its own records; a record the player wrote, a reconciliation and a bill stay, and a link to a removed record is just cleared). An import never pays: `reconcilePays` counts only records the player wrote, so importing a file cannot unlock a payment. Fingerprints and file names are inside the sealed row.

**Not built:** a statement as the other side of a reconciliation session (5.8), PDF and spreadsheet statements, mapping a description to a category, transfers inferred between two imported accounts, foreign currency columns and rates, saved column mappings per account, an `ImportBatch` table of its own (a batch is derived from its records), provider connections and `FinancialProviderConnection`.

## 22G. Overview counts and widgets

Section 3.2B in code: mint-core `OverviewCounts.kt` (`OverviewWindow`, `PayLaterCount`, `OverviewCounts.days/liabilityCounted/dueOn` and `summary` returning `OverviewSummary` and `DueItem`); `MintRules.netPosition(data, today)` and `MintRules.breakdown` (`Breakdown.kt`, `OverviewBreakdown`) use them (without a day, net position counts everything). The two settings live in `MintSettings` (sealed JSON). `MintStore.setOverviewWindow/setOverviewPayLater`; the "Overview counts" box is `OverviewCountsBox` and the breakdown dialog is in `ui/mint/Overview.kt`. `MintWidget` and `MintDueWidget` are in `androidApp/.../widgets/GlanceWidgets.kt` (receivers in the manifest, providers `widget_mint.xml` and `widget_mint_due.xml`) and refresh with the others in `updateWidgets`.

## 22H. Fields and rules the sections above rely on

- **Splits.** `Transaction.splits` carries at least two positive minor-unit parts that sum exactly to the parent (5.7).
- **Savings targets.** `SavingsTarget` stores its amount, optional date and selected accounts (7.4).
- **Statements.** `CreditStatement` records a credit card or pay-later statement balance, minimum payment and due date as reference facts (5.6B); the estimate is linked to its statement, so re-recording the same account and date cannot accrue twice.
- **Paydays** (8.3B): `Frequency.SEMI_MONTHLY`, `Bill.days`, `Bill.workday`, `Bill.estimate`; `Occurrence.payDay` is the really due day and `dueDay` the scheduled day that keys settlement, and reminders use `payDay`. `PhHolidays` and `Payday` are in mint-core `Payday.kt`; `MintSettings.builtInHolidays`, `holidaysIgnored` and `daysOff` are set by `setBuiltInHolidays`, `setHolidayCounted` and `setDayOff`; the UI is `ui/mint/Paydays.kt`.
- **Accounts.** `MintStore.arrangeAccounts(orderedIds)` puts the ids back into their existing slots of the accounts list, which is the saved order. `FinancialAccount.appPackage` is chosen from the installed apps (`AppLauncher`, `InstalledApp` in `Integrations.kt`, `AndroidAppLauncher`). `limitMinor`, `statementDay` and `dueDay` are checked by `MintRules.checkLimit` and `checkCardDay`; a short month clamps through `MintRules.nextOnDay`; `MintRules.leftToSpend` feeds the card row and options (`ui/mint/CardDays.kt`).
- **Recurring income.** `Bill.income` (default false) marks money expected; "Received, record it" goes through `settleAndRecord`.


---

## Testing reference

MINT persistence, transaction integrity, balance reconciliation, import deduplication, offline behavior and strict financial-data privacy boundaries must follow [`../ARCHITECTURE/TEST_STRATEGY.md`](../ARCHITECTURE/TEST_STRATEGY.md). Use synthetic financial fixtures only.
