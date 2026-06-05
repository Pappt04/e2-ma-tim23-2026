package uns.ac.rs.team23.server.service

import org.springframework.stereotype.Service
import uns.ac.rs.team23.server.dto.game.MojBrojResponse
import kotlin.random.Random

@Service
class MojBrojService {

    fun generate(): MojBrojResponse {
        val target = Random.nextInt(1, 1000)
        val singleDigits = (1..9).shuffled().take(4)
        val medium = listOf(10, 15, 20).random()
        val large = listOf(25, 50, 75, 100).random()
        val numbers = (singleDigits + medium + large).shuffled()
        return MojBrojResponse(targetNumber = target, numbers = numbers)
    }

    fun evaluate(expression: String, target: Int): Pair<Int, Boolean> {
        val sanitized = expression.replace(" ", "")
        require(sanitized.matches(Regex("[0-9+\\-*/()]*"))) { "Invalid characters in expression" }
        val result = evalExpression(sanitized)
        return Pair(result, result == target)
    }

    private fun evalExpression(expr: String): Int {
        val tokens = tokenize(expr)
        val pos = intArrayOf(0)
        return parseAddSub(tokens, pos)
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            if (expr[i].isDigit()) {
                val start = i
                while (i < expr.length && expr[i].isDigit()) i++
                tokens.add(expr.substring(start, i))
            } else {
                tokens.add(expr[i].toString())
                i++
            }
        }
        return tokens
    }

    private fun parseAddSub(tokens: List<String>, pos: IntArray): Int {
        var result = parseMulDiv(tokens, pos)
        while (pos[0] < tokens.size && tokens[pos[0]] in listOf("+", "-")) {
            val op = tokens[pos[0]++]
            val right = parseMulDiv(tokens, pos)
            result = if (op == "+") result + right else result - right
        }
        return result
    }

    private fun parseMulDiv(tokens: List<String>, pos: IntArray): Int {
        var result = parsePrimary(tokens, pos)
        while (pos[0] < tokens.size && tokens[pos[0]] in listOf("*", "/")) {
            val op = tokens[pos[0]++]
            val right = parsePrimary(tokens, pos)
            result = if (op == "*") result * right else {
                require(right != 0) { "Division by zero" }
                result / right
            }
        }
        return result
    }

    private fun parsePrimary(tokens: List<String>, pos: IntArray): Int {
        val token = tokens[pos[0]]
        return if (token == "(") {
            pos[0]++
            val result = parseAddSub(tokens, pos)
            require(pos[0] < tokens.size && tokens[pos[0]] == ")") { "Expected ')'" }
            pos[0]++
            result
        } else {
            pos[0]++
            token.toInt()
        }
    }
}
