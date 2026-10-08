package app.monoworkspace

import app.monoworkspace.engine.CellValue
import app.monoworkspace.engine.formula.CompiledFormula
import app.monoworkspace.engine.formula.Expr
import app.monoworkspace.engine.formula.FormulaContext
import app.monoworkspace.engine.formula.FormulaException
import app.monoworkspace.engine.formula.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class FormulaParserTest {

    private val now = ZonedDateTime.of(2026, 10, 8, 12, 0, 0, 0, ZoneOffset.UTC)

    private fun ctx(props: Map<String, CellValue> = emptyMap()) = object : FormulaContext {
        override fun prop(name: String): CellValue = props[name] ?: CellValue.Error("No property named \"$name\"")
        override fun now(): ZonedDateTime = now
    }

    private fun eval(src: String, props: Map<String, CellValue> = emptyMap()) = CompiledFormula(src).evaluate(ctx(props))

    @Test
    fun precedenceAndAssociativity() {
        assertEquals(CellValue.Num(7.0), eval("1 + 2 * 3"))
        assertEquals(CellValue.Num(9.0), eval("(1 + 2) * 3"))
        assertEquals(CellValue.Num(1.0), eval("10 - 6 - 3"))
        assertEquals(CellValue.Num(2.0), eval("8 / 2 / 2"))
        assertEquals(CellValue.Num(-4.0), eval("-2 * 2"))
    }

    @Test
    fun parsesCallsAndProps() {
        val e = Parser.parse("if(prop(\"Done\"), \"yes\", concat(\"no\", \"!\"))")
        assertTrue(e is Expr.Call)
        assertEquals(setOf("Done"), Parser.references(e))
    }

    @Test
    fun comparisonAndIf() {
        assertEquals(CellValue.Bool(true), eval("2 >= 2"))
        assertEquals(CellValue.Bool(false), eval("\"a\" == \"b\""))
        assertEquals(CellValue.Text("big"), eval("if(prop(\"N\") > 10, \"big\", \"small\")", mapOf("N" to CellValue.Num(11.0))))
    }

    @Test
    fun stringFunctions() {
        assertEquals(CellValue.Text("Task 3"), eval("concat(\"Task \", 3)"))
        assertEquals(CellValue.Num(5.0), eval("length(\"hello\")"))
        assertEquals(CellValue.Text("ab"), eval("\"a\" + \"b\""))
    }

    @Test
    fun roundAndDates() {
        assertEquals(CellValue.Num(3.14), eval("round(3.14159, 2)"))
        assertEquals(CellValue.Num(3.0), eval("round(2.5)"))
        val due = CellValue.DateTime(LocalDate.of(2026, 10, 12).atStartOfDay(ZoneOffset.UTC))
        val start = CellValue.DateTime(LocalDate.of(2026, 10, 8).atStartOfDay(ZoneOffset.UTC))
        assertEquals(CellValue.Num(4.0), eval("dateDiff(prop(\"Due\"), prop(\"Start\"), \"days\")", mapOf("Due" to due, "Start" to start)))
        assertTrue(eval("now()") is CellValue.DateTime)
    }

    @Test
    fun typeErrorsBecomeErrors() {
        val r = eval("1 + true")
        assertTrue(r is CellValue.Error)
        assertTrue(eval("length(3)") is CellValue.Error)
        assertTrue(eval("1 / 0") is CellValue.Error)
        assertTrue(eval("if(1, 2, 3)") is CellValue.Error)
        assertTrue(eval("prop(\"Missing\")") is CellValue.Error)
    }

    @Test
    fun syntaxErrorsAreReported() {
        for (bad in listOf("1 +", "(1", "foo(1)", "prop(Name)", "\"open", "1 < 2 < 3", "round()", "1 $ 2")) {
            try {
                Parser.parse(bad)
                fail("Expected failure for $bad")
            } catch (e: FormulaException) {
                assertTrue(e.message!!.isNotBlank())
            }
            assertTrue(CompiledFormula(bad).parseError != null)
        }
    }

    @Test
    fun emptyValuesActAsZeroOrBlank() {
        assertEquals(CellValue.Num(5.0), eval("prop(\"N\") + 5", mapOf("N" to CellValue.Empty)))
        assertEquals(CellValue.Text("x"), eval("concat(prop(\"T\"), \"x\")", mapOf("T" to CellValue.Empty)))
    }
}
