package app.monoworkspace

import app.monoworkspace.engine.FilterEngine
import app.monoworkspace.engine.RowInput
import app.monoworkspace.engine.RowResolver
import app.monoworkspace.engine.SortEngine
import app.monoworkspace.model.Conjunction
import app.monoworkspace.model.DatabaseSchema
import app.monoworkspace.model.FilterGroup
import app.monoworkspace.model.FilterOperator
import app.monoworkspace.model.FilterRule
import app.monoworkspace.model.FilterValue
import app.monoworkspace.model.PropertyConfig
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.SelectOption
import app.monoworkspace.model.SortDirection
import app.monoworkspace.model.SortRule
import app.monoworkspace.model.StatusGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class FilterSortTest {
    private val today = LocalDate.of(2026, 10, 8)
    private val zone = ZoneOffset.UTC
    private val now = ZonedDateTime.of(2026, 10, 8, 9, 0, 0, 0, zone)

    private val schema = DatabaseSchema(
        listOf(
            PropertyDef("title", "Name", PropertyType.TITLE, isTitle = true),
            PropertyDef(
                "status", "Status", PropertyType.STATUS,
                options = listOf(
                    SelectOption("todo", "To do", 0, StatusGroup.TODO),
                    SelectOption("doing", "Doing", 1, StatusGroup.IN_PROGRESS),
                    SelectOption("done", "Done", 2, StatusGroup.COMPLETE),
                ),
            ),
            PropertyDef("points", "Points", PropertyType.NUMBER),
            PropertyDef("due", "Due", PropertyType.DATE),
            PropertyDef("flag", "Flag", PropertyType.CHECKBOX),
            PropertyDef("double", "Double", PropertyType.FORMULA, config = PropertyConfig(expression = "prop(\"Points\") * 2")),
        ),
    )

    private val rows = listOf(
        RowInput("a", "Alpha", createdAt = 0, editedAt = 0, orderKey = "a", values = mapOf(
            "status" to PropertyValue.Select("todo"), "points" to PropertyValue.Number(3.0),
            "due" to PropertyValue.DateValue("2026-10-10"), "flag" to PropertyValue.Checkbox(true),
        )),
        RowInput("b", "beta", createdAt = 0, editedAt = 0, orderKey = "b", values = mapOf(
            "status" to PropertyValue.Select("done"), "points" to PropertyValue.Number(8.0),
            "due" to PropertyValue.DateValue("2026-09-01"),
        )),
        RowInput("c", "Gamma", createdAt = 0, editedAt = 0, orderKey = "c", values = mapOf(
            "status" to PropertyValue.Select("doing"),
        )),
        RowInput("d", "delta", createdAt = 0, editedAt = 0, orderKey = "d"),
    )

    private val resolver = RowResolver(schema, rows, zone = zone, now = now)

    private fun filter(group: FilterGroup) = rows.filter { FilterEngine.matches(it, group, resolver, today) }.map { it.id }

    private fun rule(prop: String, op: FilterOperator, v: FilterValue = FilterValue()) = FilterRule(prop + op.name, prop, op, v)

    @Test
    fun textOperators() {
        assertEquals(listOf("a", "b", "c", "d"), filter(FilterGroup(rules = listOf(rule("title", FilterOperator.CONTAINS, FilterValue(text = "A"))))))
        assertEquals(listOf("a", "b", "d"), filter(FilterGroup(rules = listOf(rule("title", FilterOperator.NOT_CONTAINS, FilterValue(text = "m"))))))
        assertEquals(listOf("b"), filter(FilterGroup(rules = listOf(rule("title", FilterOperator.IS, FilterValue(text = "BETA"))))))
    }

    @Test
    fun selectAndEmpty() {
        assertEquals(listOf("a"), filter(FilterGroup(rules = listOf(rule("status", FilterOperator.IS, FilterValue(optionIds = listOf("todo")))))))
        assertEquals(listOf("b", "c", "d"), filter(FilterGroup(rules = listOf(rule("status", FilterOperator.IS_NOT, FilterValue(optionIds = listOf("todo")))))))
        assertEquals(listOf("a", "c"), filter(FilterGroup(rules = listOf(rule("status", FilterOperator.IS_ANY_OF, FilterValue(optionIds = listOf("todo", "doing")))))))
        assertEquals(listOf("d"), filter(FilterGroup(rules = listOf(rule("status", FilterOperator.IS_EMPTY)))))
    }

    @Test
    fun numbersDatesCheckboxAndFormula() {
        assertEquals(listOf("b"), filter(FilterGroup(rules = listOf(rule("points", FilterOperator.GT, FilterValue(number = 5.0))))))
        assertEquals(listOf("a"), filter(FilterGroup(rules = listOf(rule("points", FilterOperator.BETWEEN, FilterValue(number = 1.0, number2 = 4.0))))))
        assertEquals(listOf("a"), filter(FilterGroup(rules = listOf(rule("due", FilterOperator.NEXT_WEEK)))))
        assertEquals(listOf("b"), filter(FilterGroup(rules = listOf(rule("due", FilterOperator.BEFORE, FilterValue(date = "2026-10-01"))))))
        assertEquals(listOf("a"), filter(FilterGroup(rules = listOf(rule("flag", FilterOperator.CHECKED)))))
        assertEquals(listOf("b"), filter(FilterGroup(rules = listOf(rule("double", FilterOperator.EQ, FilterValue(number = 16.0))))))
    }

    @Test
    fun nestedOrGroupInsideAnd() {
        val group = FilterGroup(
            conjunction = Conjunction.AND,
            rules = listOf(rule("status", FilterOperator.IS_NOT_EMPTY)),
            groups = listOf(
                FilterGroup(
                    id = "g", conjunction = Conjunction.OR,
                    rules = listOf(
                        rule("points", FilterOperator.GT, FilterValue(number = 5.0)),
                        rule("flag", FilterOperator.CHECKED),
                    ),
                ),
            ),
        )
        assertEquals(listOf("a", "b"), filter(group))
    }

    @Test
    fun sortPutsNullsLastInBothDirections() {
        val asc = SortEngine.sort(rows, listOf(SortRule("points", SortDirection.ASC)), resolver).map { it.id }
        assertEquals(listOf("a", "b", "c", "d"), asc)
        val desc = SortEngine.sort(rows, listOf(SortRule("points", SortDirection.DESC)), resolver).map { it.id }
        assertEquals(listOf("b", "a", "c", "d"), desc)
        val dueDesc = SortEngine.sort(rows, listOf(SortRule("due", SortDirection.DESC)), resolver).map { it.id }
        assertEquals(listOf("a", "b", "c", "d"), dueDesc)
    }

    @Test
    fun multiLevelSortAndStatusOrder() {
        val byStatus = SortEngine.sort(rows, listOf(SortRule("status")), resolver).map { it.id }
        assertEquals(listOf("a", "c", "b", "d"), byStatus)
        val byTitle = SortEngine.sort(rows, listOf(SortRule("title", SortDirection.DESC)), resolver).map { it.id }
        assertEquals(listOf("c", "d", "b", "a"), byTitle)
        val twoLevel = SortEngine.sort(
            rows,
            listOf(SortRule("flag", SortDirection.DESC), SortRule("title")),
            resolver,
        ).map { it.id }
        assertEquals(listOf("a", "b", "d", "c"), twoLevel)
    }
}
