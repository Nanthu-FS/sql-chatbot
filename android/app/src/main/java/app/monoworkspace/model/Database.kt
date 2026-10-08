package app.monoworkspace.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PropertyType(val label: String, val computed: Boolean = false) {
    TITLE("Title"),
    TEXT("Text"),
    NUMBER("Number"),
    SELECT("Select"),
    MULTI_SELECT("Multi-select"),
    STATUS("Status"),
    DATE("Date"),
    CHECKBOX("Checkbox"),
    URL("URL"),
    EMAIL("Email"),
    PHONE("Phone"),
    FILES("Files"),
    RELATION("Relation"),
    ROLLUP("Rollup", computed = true),
    FORMULA("Formula", computed = true),
    CREATED_TIME("Created time", computed = true),
    EDITED_TIME("Last edited time", computed = true),
    CREATED_BY("Created by", computed = true);

    val hasOptions: Boolean get() = this == SELECT || this == MULTI_SELECT || this == STATUS

    companion object {
        /** Types offered in the type picker (title is fixed). */
        val pickable = entries.filter { it != TITLE }
    }
}

@Serializable
enum class StatusGroup(val label: String) { TODO("To do"), IN_PROGRESS("In progress"), COMPLETE("Complete") }

@Serializable
data class SelectOption(
    val id: String,
    val name: String,
    val order: Int = 0,
    val group: StatusGroup? = null,
)

@Serializable
enum class NumberFormat(val label: String) { PLAIN("Number"), COMMA("Number with commas"), PERCENT("Percent"), CURRENCY("Currency") }

@Serializable
enum class RollupFunction(val label: String) {
    COUNT("Count"),
    COUNT_VALUES("Count values"),
    SUM("Sum"),
    AVERAGE("Average"),
    MIN("Min"),
    MAX("Max"),
    EARLIEST("Earliest date"),
    LATEST("Latest date"),
}

@Serializable
data class PropertyConfig(
    val numberFormat: NumberFormat = NumberFormat.PLAIN,
    val decimals: Int = 0,
    val currencySymbol: String = "$",
    val expression: String = "",
    val relationDatabaseId: String? = null,
    val relationTwoWay: Boolean = false,
    val relationReversePropertyId: String? = null,
    val rollupRelationPropertyId: String? = null,
    val rollupTargetPropertyId: String? = null,
    val rollupFunction: RollupFunction = RollupFunction.COUNT,
)

@Serializable
data class PropertyDef(
    val id: String,
    val name: String,
    val type: PropertyType,
    val options: List<SelectOption> = emptyList(),
    val config: PropertyConfig = PropertyConfig(),
    val isTitle: Boolean = false,
) {
    fun option(id: String?): SelectOption? = options.firstOrNull { it.id == id }
    val sortedOptions: List<SelectOption> get() = options.sortedBy { it.order }
}

@Serializable
data class DatabaseSchema(val properties: List<PropertyDef> = emptyList()) {
    val title: PropertyDef get() = properties.first { it.isTitle }
    fun property(id: String?): PropertyDef? = properties.firstOrNull { it.id == id }
    fun byName(name: String): PropertyDef? = properties.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

@Serializable
data class FileRef(val path: String, val name: String, val size: Long = 0, val mimeType: String? = null)

/** Stored value of one property on one row. Computed types are never stored. */
@Serializable
sealed interface PropertyValue {
    @Serializable @SerialName("text")
    data class Text(val value: String) : PropertyValue

    @Serializable @SerialName("number")
    data class Number(val value: Double) : PropertyValue

    @Serializable @SerialName("select")
    data class Select(val optionId: String) : PropertyValue

    @Serializable @SerialName("multi")
    data class Multi(val optionIds: List<String>) : PropertyValue

    /** ISO-8601 strings: LocalDate when includeTime is false, OffsetDateTime when true. */
    @Serializable @SerialName("date")
    data class DateValue(val start: String, val end: String? = null, val includeTime: Boolean = false) : PropertyValue

    @Serializable @SerialName("checkbox")
    data class Checkbox(val value: Boolean) : PropertyValue

    @Serializable @SerialName("relation")
    data class Relation(val rowIds: List<String>) : PropertyValue

    @Serializable @SerialName("files")
    data class Files(val files: List<FileRef>) : PropertyValue
}

@Serializable
enum class ViewType(val label: String) { TABLE("Table"), BOARD("Board"), LIST("List"), GALLERY("Gallery"), CALENDAR("Calendar") }

@Serializable
enum class Conjunction { AND, OR }

@Serializable
enum class FilterOperator(val label: String) {
    CONTAINS("contains"),
    NOT_CONTAINS("does not contain"),
    IS("is"),
    IS_NOT("is not"),
    IS_EMPTY("is empty"),
    IS_NOT_EMPTY("is not empty"),
    EQ("="),
    NEQ("≠"),
    LT("<"),
    GT(">"),
    BETWEEN("between"),
    IS_ANY_OF("is any of"),
    BEFORE("is before"),
    AFTER("is after"),
    DATE_BETWEEN("is between"),
    PAST_WEEK("past week"),
    PAST_MONTH("past month"),
    NEXT_WEEK("next week"),
    NEXT_MONTH("next month"),
    CHECKED("is checked"),
    NOT_CHECKED("is not checked"),
    RELATION_CONTAINS("contains"),
}

@Serializable
data class FilterValue(
    val text: String? = null,
    val number: Double? = null,
    val number2: Double? = null,
    val optionIds: List<String> = emptyList(),
    /** ISO LocalDate. */
    val date: String? = null,
    val date2: String? = null,
    val rowId: String? = null,
)

@Serializable
data class FilterRule(
    val id: String,
    val propertyId: String,
    val operator: FilterOperator,
    val value: FilterValue = FilterValue(),
)

@Serializable
data class FilterGroup(
    val id: String = "root",
    val conjunction: Conjunction = Conjunction.AND,
    val rules: List<FilterRule> = emptyList(),
    val groups: List<FilterGroup> = emptyList(),
) {
    val isEmpty: Boolean get() = rules.isEmpty() && groups.all { it.isEmpty }
    val ruleCount: Int get() = rules.size + groups.sumOf { it.ruleCount }
}

@Serializable
enum class SortDirection { ASC, DESC }

@Serializable
data class SortRule(val propertyId: String, val direction: SortDirection = SortDirection.ASC)

@Serializable
data class ViewConfig(
    val filter: FilterGroup = FilterGroup(),
    val sorts: List<SortRule> = emptyList(),
    val groupBy: String? = null,
    /** Null shows every property. */
    val visibleProperties: List<String>? = null,
    val propertyOrder: List<String> = emptyList(),
    val columnWidths: Map<String, Int> = emptyMap(),
    val calendarPropertyId: String? = null,
)

data class DatabaseView(
    val id: String,
    val databaseId: String,
    val name: String,
    val type: ViewType,
    val config: ViewConfig,
    val orderKey: String,
)

data class Database(
    val id: String,
    val pageId: String,
    val schema: DatabaseSchema,
)

object FilterOperators {
    fun forType(type: PropertyType): List<FilterOperator> = when (type) {
        PropertyType.TITLE, PropertyType.TEXT, PropertyType.URL, PropertyType.EMAIL, PropertyType.PHONE, PropertyType.CREATED_BY ->
            listOf(FilterOperator.CONTAINS, FilterOperator.NOT_CONTAINS, FilterOperator.IS, FilterOperator.IS_NOT, FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY)
        PropertyType.NUMBER, PropertyType.ROLLUP ->
            listOf(FilterOperator.EQ, FilterOperator.NEQ, FilterOperator.LT, FilterOperator.GT, FilterOperator.BETWEEN, FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY)
        PropertyType.FORMULA ->
            listOf(FilterOperator.CONTAINS, FilterOperator.IS, FilterOperator.EQ, FilterOperator.LT, FilterOperator.GT, FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY)
        PropertyType.SELECT, PropertyType.STATUS ->
            listOf(FilterOperator.IS, FilterOperator.IS_NOT, FilterOperator.IS_ANY_OF, FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY)
        PropertyType.MULTI_SELECT ->
            listOf(FilterOperator.CONTAINS, FilterOperator.NOT_CONTAINS, FilterOperator.IS_ANY_OF, FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY)
        PropertyType.DATE, PropertyType.CREATED_TIME, PropertyType.EDITED_TIME ->
            listOf(
                FilterOperator.IS, FilterOperator.BEFORE, FilterOperator.AFTER, FilterOperator.DATE_BETWEEN,
                FilterOperator.PAST_WEEK, FilterOperator.PAST_MONTH, FilterOperator.NEXT_WEEK, FilterOperator.NEXT_MONTH,
                FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY,
            )
        PropertyType.CHECKBOX -> listOf(FilterOperator.CHECKED, FilterOperator.NOT_CHECKED)
        PropertyType.RELATION -> listOf(FilterOperator.RELATION_CONTAINS, FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY)
        PropertyType.FILES -> listOf(FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY)
    }

    fun needsValue(op: FilterOperator): Boolean = op !in setOf(
        FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY, FilterOperator.PAST_WEEK, FilterOperator.PAST_MONTH,
        FilterOperator.NEXT_WEEK, FilterOperator.NEXT_MONTH, FilterOperator.CHECKED, FilterOperator.NOT_CHECKED,
    )
}

object DefaultSchemas {
    fun statusOptions(): List<SelectOption> = listOf(
        SelectOption("status_todo", "Not started", 0, StatusGroup.TODO),
        SelectOption("status_doing", "In progress", 1, StatusGroup.IN_PROGRESS),
        SelectOption("status_done", "Done", 2, StatusGroup.COMPLETE),
    )

    fun newDatabase(): DatabaseSchema = DatabaseSchema(
        listOf(
            PropertyDef("title", "Name", PropertyType.TITLE, isTitle = true),
            PropertyDef("status", "Status", PropertyType.STATUS, options = statusOptions()),
            PropertyDef("date", "Date", PropertyType.DATE),
        ),
    )
}
