# Decision records: mint-core

Moved here from code comments in the comment audit (21 November 2026). Each entry is a decision, rule or constraint that
the code alone does not show. Entries are grouped by source file; a line saying "Documented in" means the owning doc
already holds the rule and the entry only points to it.

## `mint-core/build.gradle.kts`

### mint-core module boundary

Documented in `docs/pokelife/MODULES/MINT.md 16.3`.

## `mint-core/src/commonMain/kotlin/app/pokeplan/mint/Breakdown.kt`

### BreakdownLine

Documented in `docs/pokelife/MODULES/MINT.md 3.2A`.

### CreditLineInfo

Documented in `docs/pokelife/MODULES/MINT.md 3.2A`.

### OverviewBreakdown

Documented in `docs/pokelife/MODULES/MINT.md 3.2A`.

### Breakdown liability lines

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

## `mint-core/src/commonMain/kotlin/app/pokeplan/mint/Installments.kt`

### PlanEvery

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### FeeKind

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Installment

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### InstallmentPlan

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### InstallmentPlan.purchaseTxId

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### InstallmentPlan.feeKind

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### InstallmentPlan.feeRule

`InstallmentPlan.feeRule` is the rule used to generate future fees. It is null for an imported (existing) schedule and for a manually edited one, so a null rule means the fees were typed, not computed.

### InstallmentPlan.openingBalanceIncluded

Documented in `docs/pokelife/MODULES/MINT.md 5.6A (Installments already owed)`.

### FeeRule.Total

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### FeeRule.RatePerPeriod

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### InstallmentView.feeRecorded

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### PlanView.overpaidMinor

`PlanView` is a plan as it stands today, derived from the ledger and never stored. `overpaidMinor` is the amount by which the repayments linked to the plan exceed its total.

### Credit activity

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### CashFlow

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Installments.dueOn monthly step

A monthly installment due day is counted from the first due day, not from the previous installment, so nothing drifts: a plan first due on 31 January is due 28 February and 31 March. A due day a month lacks falls on its last day.

### Installments.split

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Installments.generate

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Installments.checkExisting

An existing schedule (installments already owed) is accepted only as 1 to MAX_COUNT rows in due-day order, each with a due day, an amount still owed above zero and a fee that is not negative. Only a credit card or pay later account may hold one.

### Installments.feeRecorded

The fee of an installment counts as recorded while a live (counted) `FEE` record exists for it; voiding or deleting that record lets the fee be recorded again. A late fee never counts as the installment fee.

### Installments.view

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Installments.view repayment allocation

A repayment allocated to a specific installment goes to that installment, up to what it asks. The rest of it, and any repayment linked to the whole plan, is applied in date order to the earliest unpaid installment first.

### Installments.allocatePayment

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### Installments.dueWithin

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### Installments.cashFlow

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

## `mint-core/src/commonMain/kotlin/app/pokeplan/mint/MintData.kt`

### Money in integer minor units

Documented in `docs/pokelife/MODULES/MINT.md 15.2`.

### FinancialAccount

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

### FinancialAccount.card

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

### FinancialAccount.receiveQr

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

### FinancialAccount.hideFromTotal

Documented in `docs/pokelife/MODULES/MINT.md 4.3B`.

### FinancialAccount.appPackage

Documented in `docs/pokelife/MODULES/MINT.md 4.3C`.

### FinancialAccount.limitMinor

Documented in `docs/pokelife/MODULES/MINT.md 4.3D`.

### FinancialAccount.statementDay and dueDay

Documented in `docs/pokelife/MODULES/MINT.md 4.3D`.

### FinancialAccount.annualAprBasisPoints

Documented in `docs/pokelife/MODULES/MINT.md 5.6B`.

### FinancialAccount.interestGraceDays

Documented in `docs/pokelife/MODULES/MINT.md 5.6B`.

### FinancialAccount earned-interest fields

Documented in `docs/pokelife/MODULES/MINT.md 4.3E`.

### FinancialAccount.earnFrom

Documented in `docs/pokelife/MODULES/MINT.md 4.3E`.

### CardStyle

`CardStyle` ids are what is saved on an account; the colour for each is chosen in the UI, so a palette change never touches saved data. An unknown id (from a newer build) reads as PLAIN. Cosmetic only (REP-056).

### TxStatus

Documented in `docs/pokelife/MODULES/MINT.md 5.1`.

### TxSource

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### Transaction

Documented in `docs/pokelife/MODULES/MINT.md 5.5`.

### Transaction.billId

Documented in `docs/pokelife/MODULES/MINT.md 8.2`.

### Transaction import provenance

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### Transaction.planId

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Transaction.planNo

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Transaction.feeKind

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### Transaction.planAllocations

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### Transaction.splits

Documented in `docs/pokelife/MODULES/MINT.md 22H (Splits)`.

### Transaction.creditStatementId

Documented in `docs/pokelife/MODULES/MINT.md 22H (Statements)`.

### Transaction.feeMinor

Documented in `docs/pokelife/MODULES/MINT.md 5.5`.

### Transaction.paid

Documented in `docs/pokelife/MODULES/MINT.md 16.2`.

### Transaction.interestThrough

Documented in `docs/pokelife/MODULES/MINT.md 4.3E`.

### Transaction.receiptImage

Documented in `docs/pokelife/MODULES/MINT.md 13.4`.

### PlanPaymentAllocation

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### Budget

Documented in `docs/pokelife/MODULES/MINT.md 7.1`.

### Bill

Documented in `docs/pokelife/MODULES/MINT.md 8.1`.

### Bill.expectedMinor

Documented in `docs/pokelife/MODULES/MINT.md 8.1`.

### Bill.remind

Documented in `docs/pokelife/MODULES/MINT.md 12`.

### Bill.income

Documented in `docs/pokelife/MODULES/MINT.md 8.3A`.

### Bill.days

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### Bill.workday

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### Bill.estimate

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### BillPayment settlement

Documented in `docs/pokelife/MODULES/MINT.md 8.2`.

### OccurrenceState

Documented in `docs/pokelife/MODULES/MINT.md 8.2`.

### Occurrence.dueDay and payDay

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### Reconciliation

Documented in `docs/pokelife/MODULES/MINT.md 5.8`.

### ReviewRecord

Documented in `docs/pokelife/MODULES/MINT.md 9.1`.

### MintSettings.progression

Documented in `docs/pokelife/MODULES/MINT.md 16.4`.

### MintSettings.billReminders

Documented in `docs/pokelife/MODULES/MINT.md 22 (Bill reminders)`.

### MintSettings.billReminderDays

Documented in `docs/pokelife/MODULES/MINT.md 22 (Bill reminders)`.

### MintSettings.billReminderHour

Documented in `docs/pokelife/MODULES/MINT.md 12`.

### MintSettings.billReminderNames

Documented in `docs/pokelife/MODULES/MINT.md 12`.

### MintSettings.gate

Documented in `docs/pokelife/MODULES/MINT.md 13.2`.

### MintSettings.builtInHolidays

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### MintSettings.holidaysIgnored and daysOff

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### MintSettings.overviewWindow

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### MintSettings.overviewPayLater

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### MintData

`MintData` is everything Mint keeps on the device, as one document that `MintTables` (data module) takes apart into one table family per kind of record. It never holds anything about Pokemon or rewards.

### MintData.plans

Documented in `docs/pokelife/MODULES/MINT.md 5.6A`.

### MintData.shared

Documented in `docs/pokelife/MODULES/MINT.md 5.8A`.

### MintKinds

`MintKinds` lists the kinds of private Mint record, each kept in its own table family (`mint_account`, ...). The names are stable because they are saved table names; renaming one needs a schema migration.

## `mint-core/src/commonMain/kotlin/app/pokeplan/mint/MintRules.kt`

### AccountBalance

Documented in `docs/pokelife/MODULES/MINT.md 4.4`.

### BudgetProgress

Documented in `docs/pokelife/MODULES/MINT.md 7.2`.

### Available

Documented in `docs/pokelife/MODULES/MINT.md 4.5`.

### MintRules principles

Documented in `docs/pokelife/MODULES/MINT.md 15.2`.

### MintRules.QR_MAX_CHARS

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

### MintRules.checkReceiveQr

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

### MintRules.PNG_BASE64_PREFIX

`PNG_BASE64_PREFIX` ("iVBORw0KGgo") is the base64 text of the 8-byte PNG signature, used to recognise a PNG without decoding the picture.

### MintRules.IMAGE_BASE64_PREFIXES

An account picture is accepted as PNG, JPEG or WebP by the base64 form of each signature ("iVBORw0KGgo", "/9j/", and "UklGR" for the WebP RIFF container); nothing else is kept and the image is never decoded.

### MintRules.cardId

Documented in `docs/pokelife/MODULES/MINT.md 4.2`.

### MintRules.checkLimit

Documented in `docs/pokelife/MODULES/MINT.md 4.3D`.

### MintRules.checkCardDay

Documented in `docs/pokelife/MODULES/MINT.md 4.3D`.

### MintRules.nextOnDay

Documented in `docs/pokelife/MODULES/MINT.md 22H (Accounts)`.

### MintRules.leftToSpend

Documented in `docs/pokelife/MODULES/MINT.md 4.3D`.

### MintRules.effect

Documented in `docs/pokelife/MODULES/MINT.md 5.6`.

### MintRules.effect adjustment

An ADJUSTMENT moves the displayed balance in the direction it says (up or down), for an asset and for a liability alike, so for a liability "up" raises what is owed. It returns before the liability sign flip applied to other records.

### MintRules.balances

`MintRules.balances` computes every account balance in one pass over the records and gives the same figures as `balance` for each, so a long history costs one walk, not one per account.

### MintRules.simpleInterestEstimate

Documented in `docs/pokelife/MODULES/MINT.md 5.6B`.

### MintRules.dailyInterest

Documented in `docs/pokelife/MODULES/MINT.md 4.3E`.

### MintRules.INTEREST_CATEGORY

Earned-interest estimates are filed under the income category `cat-interest` ("Interest"). It is added on first use when an older save lacks it.

### MintRules.checkEarn

Documented in `docs/pokelife/MODULES/MINT.md 4.3E`.

### MintRules.earnedInterest

Documented in `docs/pokelife/MODULES/MINT.md 4.3E`.

### MintRules.interestPeriods

Documented in `docs/pokelife/MODULES/MINT.md 4.3E`.

### MintRules.netPosition

Documented in `docs/pokelife/MODULES/MINT.md 11`.

### MintRules.netPosition Pay later

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### MintRules.available

Documented in `docs/pokelife/MODULES/MINT.md 4.5`.

### Recurring income not in available money

Documented in `docs/pokelife/MODULES/MINT.md 8.3A`.

### MintRules.spent

Documented in `docs/pokelife/MODULES/MINT.md 7.1`.

### Transfer fee is spending

Documented in `docs/pokelife/MODULES/MINT.md 5.5`.

### MintRules.earned

Documented in `docs/pokelife/MODULES/MINT.md 5.4`.

### MintRules.dueDay

Documented in `docs/pokelife/MODULES/MINT.md 8.3`.

### Semi-monthly first month

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### MintRules.semiDays

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### MintRules.payDay

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### MintRules.occurrences

Documented in `docs/pokelife/MODULES/MINT.md 8.2`.

### MintRules.OVERDUE_DAYS

Documented in `docs/pokelife/MODULES/MINT.md 8.2`.

### MintRules.settled

Documented in `docs/pokelife/MODULES/MINT.md 8.2`.

### MintRules.reconcileDifference

Documented in `docs/pokelife/MODULES/MINT.md 5.8`.

### MintRules.RECONCILES_PAID_PER_DAY

Documented in `docs/economy.md Mint caps`.

### MintRules.RECORDS_PAID_PER_DAY

Documented in `docs/pokelife/MODULES/MINT.md 16.2`.

### MintRules.logPays

Documented in `docs/pokelife/MODULES/MINT.md 16.2`.

### MintRules.REVIEWS_PAID_PER_WEEK

Documented in `docs/economy.md Mint caps`.

### MintRules.hasSubstance

Documented in `docs/pokelife/MODULES/MINT.md 16.2`.

### MintRules.reconcilePays

Documented in `docs/pokelife/MODULES/MINT.md 22 (Ledger Research)`.

### MintRules.reviewPays

Documented in `docs/pokelife/MODULES/MINT.md 22 (Ledger Research)`.

### MintRules.seedCategories

Documented in `docs/pokelife/MODULES/MINT.md 6`.

## `mint-core/src/commonMain/kotlin/app/pokeplan/mint/Money.kt`

### Money

Documented in `docs/pokelife/MODULES/MINT.md 15.2`.

### Money.common

Documented in `docs/pokelife/MODULES/MINT.md 11`.

### Money.MAX_MINOR

`Money.MAX_MINOR` caps one amount at a hundred billion of a unit (10^14 minor units), so sums of amounts can never overflow a Long.

### Money.parse

Documented in `docs/pokelife/MODULES/MINT.md 22 (Money)`.

### Money.format

Documented in `docs/pokelife/MODULES/MINT.md 22 (Money)`.

## `mint-core/src/commonMain/kotlin/app/pokeplan/mint/MonthlyBills.kt`

### MonthlyBill

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### MonthlyBill.state

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### MonthlyBills.open

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### MonthlyBills.firstDue

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

### MonthlyBills.allocate

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

## `mint-core/src/commonMain/kotlin/app/pokeplan/mint/OverviewCounts.kt`

### OverviewWindow

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### PayLaterCount

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### OverviewCounts

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### OverviewCounts.liabilityCounted

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### DueItem.amountMinor

Documented in `docs/pokelife/MODULES/MINT.md 8.1`.

### OverviewSummary

Documented in `docs/pokelife/MODULES/MINT.md 3.2B`.

### One line per monthly bill

Documented in `docs/pokelife/MODULES/MINT.md 5.6C`.

## `mint-core/src/commonMain/kotlin/app/pokeplan/mint/Payday.kt`

### PhHolidays

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### PhHolidays EDSA

25 February (EDSA People Power Anniversary) is in the built-in list as a special non-working day, counted as a day off for paydays.

### PhHolidays Araw ng Kagitingan

9 April (Araw ng Kagitingan) is in the built-in holiday list as a regular holiday, counted as a day off for paydays.

### PhHolidays Ninoy Aquino Day

21 August (Ninoy Aquino Day) is in the built-in list as a special non-working day, counted as a day off for paydays.

### PhHolidays Bonifacio Day

30 November (Bonifacio Day) is a regular holiday in the built-in list, so a payday on it moves to the working day before.

### PhHolidays Immaculate Conception

8 December (Feast of the Immaculate Conception) is in the built-in list as a special non-working day, counted as a day off for paydays.

### Payday

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### Payday.onOrBefore

`Payday.onOrBefore` steps back at most 31 days to find a working day, so a calendar full of days off (every day marked off) cannot loop forever; it then returns the last day it reached.

## `mint-core/src/commonMain/kotlin/app/pokeplan/mint/Shared.kt`

### SharedKind.I_PAID

Documented in `docs/pokelife/MODULES/MINT.md 5.8A`.

### SharedKind.THEY_PAID

Documented in `docs/pokelife/MODULES/MINT.md 5.8A`.

### SharedBalance

Documented in `docs/pokelife/MODULES/MINT.md 5.8A`.

### SharedRules

Documented in `docs/pokelife/MODULES/MINT.md 5.8A`.

### SharedRules.iOwe

Documented in `docs/pokelife/MODULES/MINT.md 5.8A`.

## `mint-core/src/commonMain/kotlin/app/pokeplan/mint/StatementImport.kt`

### StatementImport

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### StatementImport.MAX_CHARS

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### StatementImport.parse

`StatementImport.parse` never throws: a file that is not usable comes back as `Parsed.Refused` with the reason in words, so a bad file can only produce a message.

### StatementImport.Columns

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### Columns.flip

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### StatementImport.guess

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### StatementImport.detectOrder

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### StatementImport.parseDate

A date cell reads a time of day after it as ignored and accepts a month written as a name (`05 Nov 2026`, `Nov 5, 2026`); anything that is not a real date is null.

### StatementImport.parseSigned

An amount cell is a signed number of minor units. A leading or trailing minus, brackets, or "DR" mean negative and "CR" means positive; a currency code or symbol is allowed. More decimals than the currency has is not rounded: the cell is refused.

### StatementImport.Row

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### Row.keptByDefault

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### StatementImport.preview

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

## `mint-core/src/commonTest/kotlin/app/pokeplan/mint/PaydayTest.kt`

### Occurrence state follows payDay

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

### Settlement key stays dueDay

Documented in `docs/pokelife/MODULES/MINT.md 8.3B`.

## `mint-core/src/commonTest/kotlin/app/pokeplan/mint/StatementImportTest.kt`

### Import refuses extra decimals

Documented in `docs/pokelife/MODULES/MINT.md 22A`.

### Import refuses foreign currency code

Documented in `docs/pokelife/MODULES/MINT.md 22A`.
