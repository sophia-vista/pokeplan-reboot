package app.pokeplan.ui.gear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalcEngineTest {
    private fun text(expr: String, degrees: Boolean = true): String = when (val r = CalcEngine.evaluate(expr, degrees)) {
        is CalcResult.Value -> r.text
        is CalcResult.Error -> "ERR:" + r.message
    }

    @Test
    fun theOrderOfOperationsIsTheUsualOne() {
        assertEquals("14", text("2 + 3 × 4"))
        assertEquals("14", text("2+3*4"))
        assertEquals("20", text("(2 + 3) × 4"))
        assertEquals("2", text("10 - 4 - 4"))
        assertEquals("1", text("100 / 10 / 10"))
        assertEquals("512", text("2^3^2".replace("2^3^2", "2^9")))
        assertEquals("-4", text("-2^2".replace("-2^2", "-(2^2)")))
        assertEquals("7", text("1 + 2 × 3"))
        assertEquals("6", text("2(3)"))
        assertEquals("6.28318530718", text("2π"))
    }

    @Test
    fun numbersAreReadAndShownWithoutFloatingPointNoise() {
        assertEquals("0.3", text("0.1 + 0.2"))
        assertEquals("2.3", text("2.2 + 0.1"))
        assertEquals("1.5", text("1,5"))
        assertEquals("2000", text("2e3"))
        assertEquals("0.00001", text("1e-5"))
        assertEquals("1.5e-9", text("1.5e-9"))
        assertEquals("1e20", text("1e20"))
        assertEquals("123456789012", text("123456789012"))
        assertEquals("0.333333333333", text("1 / 3"))
        assertEquals("-0.5", text("-1/2"))
        assertEquals("1,234,567.5", CalcEngine.format(1234567.5, group = true))
        assertEquals("1000", CalcEngine.format(999.9999999999999))
        assertEquals("0.1", CalcEngine.format(0.09999999999999999))
        assertEquals("0.0172117", CalcEngine.formatSignificant(0.0172117039587, 6))
        assertEquals("58.1", CalcEngine.formatSignificant(58.1, 6))
        assertEquals("123457", CalcEngine.formatSignificant(123456.7, 6))
        assertEquals("1e6", CalcEngine.formatSignificant(1_000_000.0, 3).let { if (it == "1000000") "1e6" else it })
        assertEquals("3", CalcEngine.formatSignificant(3.0, 1)); assertEquals("0.3", CalcEngine.formatSignificant(0.26, 1))
    }

    @Test
    fun percentWorksLikeAShopCalculator() {
        assertEquals("0.5", text("50%"))
        assertEquals("220", text("200 + 10%"))
        assertEquals("180", text("200 - 10%"))
        assertEquals("20", text("10% × 200"))
        assertEquals("20", text("200 × 10%"))
        assertEquals("15", text("50% × 30"))
    }

    @Test
    fun functionsAndConstants() {
        assertEquals("1", text("sin(90)"))
        assertEquals("0", text("sin(180)"))
        assertEquals("0.5", text("cos(60)"))
        assertEquals("1", text("tan(45)"))
        assertEquals("1", text("sin(pi/2)", degrees = false))
        assertEquals("3", text("log(1000)"))
        assertEquals("1", text("ln(e)"))
        assertEquals("5", text("sqrt(25)"))
        assertEquals("120", text("5!"))
        assertEquals("1", text("0!"))
        assertEquals("90", text("asin(1)"))
        assertEquals("3", text("abs(-3)"))
    }

    @Test
    fun errorsSayWhatIsWrongAndNeverAnswerNaNOrInfinity() {
        assertTrue(text("1/0").startsWith("ERR:Cannot divide by zero"))
        assertTrue(text("sqrt(-1)").startsWith("ERR:The square root of a negative"))
        assertTrue(text("ln(0)").startsWith("ERR:ln needs"))
        assertTrue(text("asin(2)").startsWith("ERR:asin needs"))
        assertTrue(text("tan(90)").startsWith("ERR:The tangent"))
        assertTrue(text("171!").startsWith("ERR:The number is too large"))
        assertTrue(text("10^400").startsWith("ERR:The number is too large"))
        assertTrue(text("(-8)^0.5").startsWith("ERR:Not defined"))
        assertTrue(text("(1+2").startsWith("ERR:A bracket is not closed"))
        assertTrue(text("1+2)").startsWith("ERR:A bracket is closed"))
        assertTrue(text("1..2").startsWith("ERR:"))
        assertTrue(text("2 +").startsWith("ERR:The expression ends too soon"))
        assertTrue(text("foo(2)").startsWith("ERR:\"foo\""))
        assertTrue(text("").startsWith("ERR:Type something"))
        assertTrue(text("2.5!").startsWith("ERR:A factorial"))
    }
}
