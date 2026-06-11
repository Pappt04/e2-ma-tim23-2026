package uns.ac.rs.team23.slagalica.utils

import uns.ac.rs.team23.slagalica.viewmodels.ExprToken

object ExpressionEvaluator {
    fun evaluate(tokens: List<ExprToken>): Int? {
        if (tokens.isEmpty()) return null
        return try {
            val parser = Parser(tokens)
            val result = parser.expr()
            if (parser.pos == tokens.size) result else null
        } catch (_: Exception) {
            null
        }
    }
}

private class Parser(val tokens: List<ExprToken>) {
    var pos = 0

    fun expr(): Int {
        var result = term()
        while (pos < tokens.size) {
            val op = tokens[pos] as? ExprToken.Op ?: break
            if (op.symbol != "+" && op.symbol != "-") break
            pos++
            val right = term()
            result = if (op.symbol == "+") result + right else result - right
        }
        return result
    }

    fun term(): Int {
        var result = factor()
        while (pos < tokens.size) {
            val op = tokens[pos] as? ExprToken.Op ?: break
            if (op.symbol != "*" && op.symbol != "/") break
            pos++
            val right = factor()
            if (op.symbol == "/") {
                if (right == 0 || result % right != 0) throw ArithmeticException()
                result /= right
            } else {
                result *= right
            }
        }
        return result
    }

    fun factor(): Int = when (val t = tokens[pos]) {
        is ExprToken.Num -> { pos++; t.value }
        ExprToken.OpenParen -> {
            pos++
            val inner = expr()
            if (tokens[pos] !is ExprToken.CloseParen) throw IllegalStateException()
            pos++
            inner
        }
        else -> throw IllegalStateException()
    }
}
