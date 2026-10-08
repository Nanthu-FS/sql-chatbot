package app.monoworkspace.engine

import app.monoworkspace.model.FileRef
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.SelectOption
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class RowRef(val id: String, val title: String, val deleted: Boolean = false)

/** A resolved property value, ready for display, filtering, sorting and formulas. */
sealed interface CellValue {
    data object Empty : CellValue
    data class Text(val value: String) : CellValue
    data class Num(val value: Double) : CellValue
    data class Bool(val value: Boolean) : CellValue
    data class DateTime(val start: ZonedDateTime, val end: ZonedDateTime? = null, val includeTime: Boolean = false) : CellValue
    data class Options(val options: List<SelectOption>) : CellValue
    data class Rows(val rows: List<RowRef>) : CellValue
    data class Files(val files: List<FileRef>) : CellValue
    data class Error(val message: String) : CellValue

    val isEmpty: Boolean
        get() = when (this) {
            Empty -> true
            is Text -> value.isEmpty()
            is Options -> options.isEmpty()
            is Rows -> rows.none { !it.deleted }
            is Files -> files.isEmpty()
            else -> false
        }
}

object Dates {
    /** Parses a stored date value into zoned date-times in [zone]. */
    fun parse(value: PropertyValue.DateValue, zone: ZoneId): CellValue.DateTime? {
        val start = parseOne(value.start, value.includeTime, zone) ?: return null
        val end = value.end?.let { parseOne(it, value.includeTime, zone) }
        return CellValue.DateTime(start, end, value.includeTime)
    }

    fun parseOne(text: String, includeTime: Boolean, zone: ZoneId): ZonedDateTime? = runCatching {
        if (includeTime || text.length > 10) {
            OffsetDateTime.parse(text).atZoneSameInstant(zone)
        } else {
            LocalDate.parse(text).atStartOfDay(zone)
        }
    }.recoverCatching { LocalDate.parse(text.take(10)).atStartOfDay(zone) }.getOrNull()

    fun fromEpoch(ms: Long, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), zone)

    fun store(date: ZonedDateTime, includeTime: Boolean): String =
        if (includeTime) date.toOffsetDateTime().toString() else date.toLocalDate().toString()
}
