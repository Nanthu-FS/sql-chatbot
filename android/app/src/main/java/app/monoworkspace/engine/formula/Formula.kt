package app.monoworkspace.engine.formula

import app.monoworkspace.engine.CellValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class FormulaException(message: String) : Exception(message)

// ---------- Lexer ----------

enum class TokenType { NUMBER, STRING, IDENT, LPAREN, RPAREN, COMMA, OP, EOF }

data class Token(val type: TokenType, val text: String, val pos: Int)

object Lexer {
    private val twoCharOps = setOf("==", "!=", "<=", ">=")
    private const val singleOps = "+-*/<>"

    fun tokenize(src: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || (c == '.' && i + 1 < src.length && src[i + 1].isDigit()) -> {
                    val start = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    val text = src.substring(start, i)
                    if (text.count { it == '.' } > 1) throw FormulaException("Malformed number '$text' at ${start + 1}")
                    out.add(Token(TokenType.NUMBER, text, start))
                }
                c == '"' -> {
                    val start = i
                    i++
                    val sb = StringBuilder()
                    var closed = false
                    while (i < src.length) {
                        val ch = src[i]
                        if (ch == '\\' && i + 1 < src.length) {
                            sb.append(
                                when (val n = src[i + 1]) {
                                    'n' -> '\n'
                                    't' -> '\t'
                                    else -> n
                                },
                            )
                            i += 2
                        } else if (ch == '"') {
                            closed = true
                            i++
                            break
                        } else {
                            sb.append(ch)
                            i++
                        }
                    }
                    if (!closed) throw FormulaException("Unclosed string at ${start + 1}")
                    out.add(Token(TokenType.STRING, sb.toString(), start))
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    out.add(Token(TokenType.IDENT, src.substring(start, i), start))
                }
                c == '(' -> { out.add(Token(TokenType.LPAREN, "(", i)); i++ }
                c == ')' -> { out.add(Token(TokenType.RPAREN, ")", i)); i++ }
                c == ',' -> { out.add(Token(TokenType.COMMA, ",", i)); i++ }
                i + 1 < src.length && src.substring(i, i + 2) in twoCharOps -> {
                    out.add(Token(TokenType.OP, src.substring(i, i + 2), i)); i += 2
                }
                c in singleOps -> { out.add(Token(TokenType.OP, c.toString(), i)); i++ }
                else -> throw FormulaException("Unexpected '$c' at ${i + 1}")
            }
        }
        out.add(Token(TokenType.EOF, "", src.length))
        return out
    }
}

// ---------- AST ----------

sealed interface Expr {
    data class Num(val value: Double) : Expr
    data class Str(val value: String) : Expr
    data class Bool(val value: Boolean) : Expr
    data class Unary(val op: String, val operand: Expr) : Expr
    data class Binary(val op: String, val left: Expr, val right: Expr) : Expr
    data class Call(val name: String, val args: List<Expr>) : Expr
}

// ---------- Parser ----------

/**
 * Grammar:
 *   expr       := comparison
 *   comparison := additive (("==" | "!=" | "<" | ">" | "<=" | ">=") additive)?
 *   additive   := term (("+" | "-") term)*
 *   term       := unary (("*" | "/") unary)*
 *   unary      := "-" unary | primary
 *   primary    := NUMBER | STRING | true | false | IDENT "(" args ")" | "(" expr ")"
 */
class Parser private constructor(private val tokens: List<Token>) {
    private var pos = 0

    private fun peek() = tokens[pos]
    private fun next() = tokens[pos++]

    private fun expect(type: TokenType, what: String): Token {
        val t = next()
        if (t.type != type) throw FormulaException("Expected $what at ${t.pos + 1}" + if (t.type == TokenType.EOF) " (end of formula)" else ", found '${t.text}'")
        return t
    }

    private fun parseExpr(): Expr = parseComparison()

    private fun parseComparison(): Expr {
        val left = parseAdditive()
        val t = peek()
        if (t.type == TokenType.OP && t.text in setOf("==", "!=", "<", ">", "<=", ">=")) {
            next()
            val right = parseAdditive()
            val n = peek()
            if (n.type == TokenType.OP && n.text in setOf("==", "!=", "<", ">", "<=", ">=")) {
                throw FormulaException("Chained comparison at ${n.pos + 1}; use if() to combine")
            }
            return Expr.Binary(t.text, left, right)
        }
        return left
    }

    private fun parseAdditive(): Expr {
        var left = parseTerm()
        while (peek().type == TokenType.OP && (peek().text == "+" || peek().text == "-")) {
            val op = next().text
            left = Expr.Binary(op, left, parseTerm())
        }
        return left
    }

    private fun parseTerm(): Expr {
        var left = parseUnary()
        while (peek().type == TokenType.OP && (peek().text == "*" || peek().text == "/")) {
            val op = next().text
            left = Expr.Binary(op, left, parseUnary())
        }
        return left
    }

    private fun parseUnary(): Expr {
        if (peek().type == TokenType.OP && peek().text == "-") {
            next()
            return Expr.Unary("-", parseUnary())
        }
        return parsePrimary()
    }

    private fun parsePrimary(): Expr {
        val t = next()
        return when (t.type) {
            TokenType.NUMBER -> Expr.Num(t.text.toDouble())
            TokenType.STRING -> Expr.Str(t.text)
            TokenType.LPAREN -> {
                val e = parseExpr()
                expect(TokenType.RPAREN, "')'")
                e
            }
            TokenType.IDENT -> when (t.text) {
                "true" -> Expr.Bool(true)
                "false" -> Expr.Bool(false)
                else -> {
                    if (peek().type != TokenType.LPAREN) throw FormulaException("Unknown name '${t.text}' at ${t.pos + 1}")
                    next()
                    val args = ArrayList<Expr>()
                    if (peek().type != TokenType.RPAREN) {
                        args.add(parseExpr())
                        while (peek().type == TokenType.COMMA) {
                            next()
                            args.add(parseExpr())
                        }
                    }
                    expect(TokenType.RPAREN, "')'")
                    val name = t.text
                    if (name !in Functions.arity) throw FormulaException("Unknown function '$name'")
                    val range = Functions.arity.getValue(name)
                    if (args.size !in range) {
                        throw FormulaException("$name() takes ${describe(range)} argument${if (range.last == 1) "" else "s"}, got ${args.size}")
                    }
                    if (name == "prop" && args.first() !is Expr.Str) throw FormulaException("prop() needs a quoted property name")
                    Expr.Call(name, args)
                }
            }
            TokenType.EOF -> throw FormulaException("Unexpected end of formula")
            else -> throw FormulaException("Unexpected '${t.text}' at ${t.pos + 1}")
        }
    }

    private fun describe(range: IntRange): String = when {
        range.first == range.last -> "${range.first}"
        range.last == Int.MAX_VALUE -> "at least ${range.first}"
        else -> "${range.first}–${range.last}"
    }

    companion object {
        fun parse(src: String): Expr {
            if (src.isBlank()) throw FormulaException("Formula is empty")
            val p = Parser(Lexer.tokenize(src))
            val e = p.parseExpr()
            val t = p.peek()
            if (t.type != TokenType.EOF) throw FormulaException("Unexpected '${t.text}' at ${t.pos + 1}")
            return e
        }

        /** Names of properties referenced through prop("..."). */
        fun references(expr: Expr): Set<String> = when (expr) {
            is Expr.Call -> if (expr.name == "prop") setOf((expr.args[0] as Expr.Str).value) else expr.args.flatMap { references(it) }.toSet()
            is Expr.Binary -> references(expr.left) + references(expr.right)
            is Expr.Unary -> references(expr.operand)
            else -> emptySet()
        }
    }
}

object Functions {
    val arity: Map<String, IntRange> = mapOf(
        "prop" to 1..1,
        "if" to 3..3,
        "concat" to 1..Int.MAX_VALUE,
        "length" to 1..1,
        "round" to 1..2,
        "now" to 0..0,
        "dateDiff" to 3..3,
    )
}

// ---------- Evaluator ----------

interface FormulaContext {
    fun prop(name: String): CellValue
    fun now(): ZonedDateTime
}

object Evaluator {

    fun evaluate(expr: Expr, ctx: FormulaContext): CellValue = when (expr) {
        is Expr.Num -> CellValue.Num(expr.value)
        is Expr.Str -> CellValue.Text(expr.value)
        is Expr.Bool -> CellValue.Bool(expr.value)
        is Expr.Unary -> CellValue.Num(-num(evaluate(expr.operand, ctx), "-"))
        is Expr.Binary -> binary(expr.op, evaluate(expr.left, ctx), evaluate(expr.right, ctx))
        is Expr.Call -> call(expr, ctx)
    }

    private fun call(expr: Expr.Call, ctx: FormulaContext): CellValue = when (expr.name) {
        "prop" -> {
            val v = ctx.prop((expr.args[0] as Expr.Str).value)
            if (v is CellValue.Error) throw FormulaException(v.message) else normalize(v)
        }
        "if" -> {
            val cond = evaluate(expr.args[0], ctx)
            val b = when (cond) {
                is CellValue.Bool -> cond.value
                CellValue.Empty -> false
                else -> throw FormulaException("if() condition must be true or false, got ${typeName(cond)}")
            }
            evaluate(if (b) expr.args[1] else expr.args[2], ctx)
        }
        "concat" -> CellValue.Text(expr.args.joinToString("") { text(evaluate(it, ctx)) })
        "length" -> {
            val v = evaluate(expr.args[0], ctx)
            if (v !is CellValue.Text && v != CellValue.Empty) throw FormulaException("length() needs text, got ${typeName(v)}")
            CellValue.Num(text(v).length.toDouble())
        }
        "round" -> {
            val n = num(evaluate(expr.args[0], ctx), "round")
            val digits = if (expr.args.size > 1) num(evaluate(expr.args[1], ctx), "round").toInt() else 0
            if (digits !in 0..10) throw FormulaException("round() digits must be 0–10")
            if (n.isNaN() || n.isInfinite()) throw FormulaException("round() of an invalid number")
            CellValue.Num(BigDecimal(n).setScale(digits, RoundingMode.HALF_UP).toDouble())
        }
        "now" -> CellValue.DateTime(ctx.now(), includeTime = true)
        "dateDiff" -> {
            val a = date(evaluate(expr.args[0], ctx), "dateDiff")
            val b = date(evaluate(expr.args[1], ctx), "dateDiff")
            val unitName = evaluate(expr.args[2], ctx)
            if (unitName !is CellValue.Text) throw FormulaException("dateDiff() unit must be text like \"days\"")
            val unit = when (unitName.value.lowercase()) {
                "minutes" -> ChronoUnit.MINUTES
                "hours" -> ChronoUnit.HOURS
                "days" -> ChronoUnit.DAYS
                "weeks" -> ChronoUnit.WEEKS
                "months" -> ChronoUnit.MONTHS
                "years" -> ChronoUnit.YEARS
                else -> throw FormulaException("Unknown unit \"${unitName.value}\"")
            }
            CellValue.Num(unit.between(b, a).toDouble())
        }
        else -> throw FormulaException("Unknown function '${expr.name}'")
    }

    private fun binary(op: String, l: CellValue, r: CellValue): CellValue = when (op) {
        "+" -> if (l is CellValue.Text || r is CellValue.Text) {
            CellValue.Text(text(l) + text(r))
        } else {
            CellValue.Num(num(l, "+") + num(r, "+"))
        }
        "-" -> CellValue.Num(num(l, "-") - num(r, "-"))
        "*" -> CellValue.Num(num(l, "*") * num(r, "*"))
        "/" -> {
            val d = num(r, "/")
            if (d == 0.0) throw FormulaException("Division by zero")
            CellValue.Num(num(l, "/") / d)
        }
        "==" -> CellValue.Bool(equal(l, r))
        "!=" -> CellValue.Bool(!equal(l, r))
        "<", ">", "<=", ">=" -> {
            val c = compare(l, r, op)
            CellValue.Bool(
                when (op) {
                    "<" -> c < 0
                    ">" -> c > 0
                    "<=" -> c <= 0
                    else -> c >= 0
                },
            )
        }
        else -> throw FormulaException("Unknown operator $op")
    }

    private fun equal(l: CellValue, r: CellValue): Boolean = when {
        l == CellValue.Empty && r == CellValue.Empty -> true
        l is CellValue.Num && r is CellValue.Num -> l.value == r.value
        l is CellValue.Text && r is CellValue.Text -> l.value == r.value
        l is CellValue.Bool && r is CellValue.Bool -> l.value == r.value
        l is CellValue.DateTime && r is CellValue.DateTime -> l.start.toInstant() == r.start.toInstant()
        l == CellValue.Empty && r is CellValue.Text -> r.value.isEmpty()
        r == CellValue.Empty && l is CellValue.Text -> l.value.isEmpty()
        else -> false
    }

    private fun compare(l: CellValue, r: CellValue, op: String): Int = when {
        (l is CellValue.Num || l == CellValue.Empty) && (r is CellValue.Num || r == CellValue.Empty) ->
            num(l, op).compareTo(num(r, op))
        l is CellValue.Text && r is CellValue.Text -> l.value.compareTo(r.value)
        l is CellValue.DateTime && r is CellValue.DateTime -> l.start.toInstant().compareTo(r.start.toInstant())
        l is CellValue.Bool && r is CellValue.Bool -> l.value.compareTo(r.value)
        else -> throw FormulaException("Cannot compare ${typeName(l)} with ${typeName(r)}")
    }

    private fun num(v: CellValue, op: String): Double = when (v) {
        is CellValue.Num -> v.value
        CellValue.Empty -> 0.0
        is CellValue.Bool -> throw FormulaException("'$op' needs numbers, got true/false")
        else -> throw FormulaException("'$op' needs numbers, got ${typeName(v)}")
    }

    private fun date(v: CellValue, fn: String): ZonedDateTime = when (v) {
        is CellValue.DateTime -> v.start
        else -> throw FormulaException("$fn() needs dates, got ${typeName(v)}")
    }

    fun text(v: CellValue): String = when (v) {
        CellValue.Empty -> ""
        is CellValue.Text -> v.value
        is CellValue.Num -> formatNumber(v.value)
        is CellValue.Bool -> v.value.toString()
        is CellValue.DateTime -> if (v.includeTime) v.start.toOffsetDateTime().toString() else v.start.toLocalDate().toString()
        is CellValue.Options -> v.options.joinToString(", ") { it.name }
        is CellValue.Rows -> v.rows.filter { !it.deleted }.joinToString(", ") { it.title }
        is CellValue.Files -> v.files.joinToString(", ") { it.name }
        is CellValue.Error -> throw FormulaException(v.message)
    }

    fun formatNumber(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite() && kotlin.math.abs(d) < 1e15) d.toLong().toString()
        else BigDecimal(d).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** Maps property-only shapes to formula types. */
    private fun normalize(v: CellValue): CellValue = when (v) {
        is CellValue.Options -> if (v.options.isEmpty()) CellValue.Empty else CellValue.Text(v.options.joinToString(", ") { it.name })
        is CellValue.Rows -> CellValue.Text(text(v))
        is CellValue.Files -> CellValue.Text(text(v))
        else -> v
    }

    fun typeName(v: CellValue): String = when (v) {
        CellValue.Empty -> "empty"
        is CellValue.Text -> "text"
        is CellValue.Num -> "number"
        is CellValue.Bool -> "true/false"
        is CellValue.DateTime -> "date"
        is CellValue.Options -> "options"
        is CellValue.Rows -> "relation"
        is CellValue.Files -> "files"
        is CellValue.Error -> "error"
    }
}

/** Parse once, evaluate many. Errors become [CellValue.Error]. */
class CompiledFormula(val source: String) {
    val parseError: String?
    private val expr: Expr?

    init {
        var e: Expr? = null
        var err: String? = null
        try {
            e = Parser.parse(source)
        } catch (ex: FormulaException) {
            err = ex.message
        }
        expr = e
        parseError = err
    }

    val references: Set<String> get() = expr?.let { Parser.references(it) } ?: emptySet()

    fun evaluate(ctx: FormulaContext): CellValue {
        if (parseError != null) return CellValue.Error(parseError)
        return try {
            Evaluator.evaluate(expr!!, ctx)
        } catch (ex: FormulaException) {
            CellValue.Error(ex.message ?: "Error")
        } catch (ex: ArithmeticException) {
            CellValue.Error(ex.message ?: "Arithmetic error")
        }
    }
}
