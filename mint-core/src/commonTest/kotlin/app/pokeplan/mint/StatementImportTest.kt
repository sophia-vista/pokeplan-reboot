package app.pokeplan.mint

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatementImportTest {
    private val account = FinancialAccount("a1", "Wallet", AccountType.EWALLET, "PHP", 0, LocalDate(2026, 1, 1))
    private fun table(text: String) = (StatementImport.parse(text) as StatementImport.Parsed.Table).rows

    @Test fun parsesQuotesDelimitersAndLineBreaksInAField() {
        val t = table("﻿Date;Description;Amount\r\n05/11/2026;\"Coffee; \"\"large\"\"\";-1.250,50\r\n06/11/2026;\"two\nlines\";100,00\r\n")
        assertEquals(3, t.size)
        assertEquals(listOf("05/11/2026", "Coffee; \"large\"", "-1.250,50"), t[1])
        assertEquals("two\nlines", t[2][1])
    }

    @Test fun refusesFilesThatAreNotUsable() {
        assertIs<StatementImport.Parsed.Refused>(StatementImport.parse(""))
        assertIs<StatementImport.Parsed.Refused>(StatementImport.parse("Date,Amount"))
        assertIs<StatementImport.Parsed.Refused>(StatementImport.parse("Date,Amount\n\"2026-01-01,5"))
        assertIs<StatementImport.Parsed.Refused>(StatementImport.parse("a".repeat(StatementImport.MAX_CHARS + 1)))
        assertIs<StatementImport.Parsed.Refused>(StatementImport.parse("Date,Amount\n1\u0000,2"))
    }

    @Test fun guessesColumnsFromTheHeader() {
        val c = StatementImport.guess(listOf("Posted Date", "Description", "Withdrawal", "Deposit", "Balance", "Reference No."))
        assertEquals(0, c.date); assertEquals(1, c.description); assertEquals(2, c.debit); assertEquals(3, c.credit); assertNull(c.amount); assertEquals(5, c.reference)
        val one = StatementImport.guess(listOf("Date", "Details", "Amount", "Running Balance"))
        assertEquals(2, one.amount); assertTrue(one.usable)
    }

    @Test fun datesAreReadInTheOrderTheFileUsesAndAnAmbiguousOneIsNotGuessedSilently() {
        assertEquals(StatementImport.DateOrder.DAY_FIRST, StatementImport.detectOrder(listOf("03/04/2026", "25/04/2026")))
        assertEquals(StatementImport.DateOrder.MONTH_FIRST, StatementImport.detectOrder(listOf("03/04/2026", "04/25/2026")))
        assertEquals(StatementImport.DateOrder.YEAR_FIRST, StatementImport.detectOrder(listOf("2026-04-03")))
        assertNull(StatementImport.detectOrder(listOf("03/04/2026", "05/06/2026")))
        assertEquals(LocalDate(2026, 4, 3), StatementImport.parseDate("03/04/2026", StatementImport.DateOrder.DAY_FIRST))
        assertEquals(LocalDate(2026, 3, 4), StatementImport.parseDate("03/04/2026", StatementImport.DateOrder.MONTH_FIRST))
        assertEquals(LocalDate(2026, 11, 5), StatementImport.parseDate("2026-11-05 14:30:00", StatementImport.DateOrder.DAY_FIRST))
        assertEquals(LocalDate(2026, 11, 5), StatementImport.parseDate("05 Nov 2026", StatementImport.DateOrder.MONTH_FIRST))
        assertEquals(LocalDate(2026, 11, 5), StatementImport.parseDate("Nov 5, 2026", StatementImport.DateOrder.DAY_FIRST))
        assertNull(StatementImport.parseDate("31/02/2026", StatementImport.DateOrder.DAY_FIRST))
        assertNull(StatementImport.parseDate("soon", StatementImport.DateOrder.DAY_FIRST))
    }

    @Test fun amountsAreExactAndSigned() {
        assertEquals(-125_050, StatementImport.parseSigned("-1,250.50", "PHP"))
        assertEquals(-125_050, StatementImport.parseSigned("(1,250.50)", "PHP"))
        assertEquals(-125_050, StatementImport.parseSigned("PHP 1,250.50 DR", "PHP"))
        assertEquals(125_050, StatementImport.parseSigned("₱1,250.50", "PHP"))
        assertEquals(125_050, StatementImport.parseSigned("1.250,50 CR", "PHP"))
        assertEquals(-125_050, StatementImport.parseSigned("1250.50-", "PHP"))
        assertEquals(30, StatementImport.parseSigned("0.30", "PHP"))
        assertNull(StatementImport.parseSigned("1.234", "PHP"))
        assertNull(StatementImport.parseSigned("n/a", "PHP"))
        assertNull(StatementImport.parseSigned("12 USD", "PHP"))
        assertNull(StatementImport.parseSigned("", "PHP"))
    }

    private val csv = "Date,Description,Amount,Reference\n2026-11-01,Coffee,-120.00,\n2026-11-01,Coffee,-120.00,\n2026-11-02,Salary,\"25,000.00\",R1\nbad,Rent,-5.00,\n2026-11-03,Free,0.00,\n"

    @Test fun previewMarksNewDuplicateAndProblemRowsAndKeepsTwoIdenticalRowsApart() {
        val rows = table(csv)
        val p = StatementImport.preview(rows, account, StatementImport.guess(rows.first()), StatementImport.DateOrder.YEAR_FIRST, MintData(accounts = listOf(account)))
        assertEquals(3, p.new); assertEquals(2, p.problems); assertEquals(0, p.duplicates)
        assertEquals(3, p.rows.map { it.fingerprint }.filter { it.isNotEmpty() }.toSet().size)
        assertEquals(TxType.INCOME, p.rows[2].type); assertEquals(2_500_000, p.rows[2].amountMinor)
        assertEquals(LocalDate(2026, 11, 1), p.firstDay); assertEquals(LocalDate(2026, 11, 3), p.lastDay)
    }

    @Test fun aRecordYouWroteThatLooksTheSameIsKeptApartNotSilentlyMerged() {
        val mine = Transaction("t1", TxType.EXPENSE, 12_000, "PHP", 0, LocalDate(2026, 11, 1), "a1")
        val rows = table(csv)
        val p = StatementImport.preview(rows, account, StatementImport.guess(rows.first()), StatementImport.DateOrder.YEAR_FIRST, MintData(accounts = listOf(account), transactions = listOf(mine)))
        assertEquals(2, p.possible); assertEquals(1, p.new)
        assertTrue(p.rows.filter { it.state == StatementImport.RowState.POSSIBLE }.none { it.keptByDefault })
    }

    @Test fun twoColumnStatementsAndFlippedCreditCardsReadTheirDirection() {
        val two = table("Date,Details,Withdrawal,Deposit\n2026-11-01,ATM,500.00,\n2026-11-02,Pay,,1000.00\n")
        val p = StatementImport.preview(two, account, StatementImport.guess(two.first()), StatementImport.DateOrder.YEAR_FIRST, MintData())
        assertEquals(listOf(-50_000L, 100_000L), p.rows.map { it.signedMinor })
        val card = table("Date,Description,Amount\n2026-11-01,Shop,300.00\n")
        val q = StatementImport.preview(card, account, StatementImport.guess(card.first()).copy(flip = true), StatementImport.DateOrder.YEAR_FIRST, MintData())
        assertEquals(TxType.EXPENSE, q.rows.single().type)
    }
}
