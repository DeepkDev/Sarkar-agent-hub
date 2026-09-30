package com.example.tools

import com.example.contracts.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow

typealias BaseTool = Tool

/**
 * Safe Arithmetic Calculator Tool (NO eval).
 * Uses recursive descent parser for +, -, *, /, %, ^, parentheses, negative numbers.
 */
class CalculatorTool : BaseTool {
    override val manifest = ToolManifest(
        id = "calculator",
        name = "Math Calculator",
        version = "1.0.0",
        description = "Evaluates arithmetic expressions (+, -, *, /, %, ^, parentheses) safely without eval.",
        category = "Math & Logic",
        inputParameters = listOf(
            ToolParameter(
                name = "expression",
                type = "string",
                description = "Mathematical expression string, e.g. '(15 * 4) + 120 / 3'"
            )
        ),
        permissionLevel = PermissionLevel.SAFE,
        sideEffects = SideEffectType.NONE,
        idempotent = true,
        status = CapabilityStatus.IMPLEMENTED
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val startTime = System.currentTimeMillis()
        val expr = args["expression"]?.toString()
            ?: return ToolResult("calculator", false, "Missing 'expression' argument", 0L)

        return try {
            val result = SafeMathParser.evaluate(expr)
            val duration = System.currentTimeMillis() - startTime
            ToolResult(
                toolId = "calculator",
                success = true,
                data = "{\"expression\": \"${expr.replace("\"", "\\\"")}\", \"result\": $result}",
                executionTimeMs = duration
            )
        } catch (e: Exception) {
            ToolResult(
                toolId = "calculator",
                success = false,
                data = "Calculation error: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

object SafeMathParser {
    fun evaluate(expression: String): Double {
        val sanitized = expression.replace("\\s+".toRegex(), "")
        if (sanitized.isEmpty()) throw IllegalArgumentException("Empty expression")
        return MathExpressionParser(sanitized).parse()
    }

    private class MathExpressionParser(private val text: String) {
        private var pos = 0

        private fun peek(): Char? = if (pos < text.length) text[pos] else null
        private fun get(): Char = text[pos++]

        fun parse(): Double {
            val result = parseExpression()
            if (pos < text.length) {
                throw IllegalArgumentException("Unexpected token '${text[pos]}' at position $pos")
            }
            return result
        }

        private fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { get(); v += parseTerm() }
                    '-' -> { get(); v -= parseTerm() }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                when (peek()) {
                    '*' -> { get(); v *= parseFactor() }
                    '/' -> {
                        get()
                        val denom = parseFactor()
                        if (denom == 0.0) throw ArithmeticException("Division by zero")
                        v /= denom
                    }
                    '%' -> {
                        get()
                        val denom = parseFactor()
                        if (denom == 0.0) throw ArithmeticException("Modulo by zero")
                        v %= denom
                    }
                    else -> return v
                }
            }
        }

        private fun parseFactor(): Double {
            var v = parsePower()
            if (peek() == '^') {
                get()
                v = v.pow(parseFactor())
            }
            return v
        }

        private fun parsePower(): Double {
            val ch = peek() ?: throw IllegalArgumentException("Unexpected end of expression")
            if (ch == '+') {
                get()
                return parsePower()
            }
            if (ch == '-') {
                get()
                return -parsePower()
            }
            if (ch == '(') {
                get()
                val v = parseExpression()
                if (get() != ')') throw IllegalArgumentException("Missing closing parenthesis")
                return v
            }
            if (ch.isDigit() || ch == '.') {
                val start = pos
                while (peek() != null && (peek()!!.isDigit() || peek() == '.')) {
                    get()
                }
                return text.substring(start, pos).toDouble()
            }
            throw IllegalArgumentException("Invalid character: $ch at position $pos")
        }
    }
}

/**
 * Current Date & Time Tool (Timezone-Aware)
 */
class DateTimeTool : BaseTool {
    override val manifest = ToolManifest(
        id = "datetime",
        name = "Current Date & Time",
        version = "1.0.0",
        description = "Returns current date, time, day of week, and timezone offset.",
        category = "System",
        inputParameters = listOf(
            ToolParameter(
                name = "timezone",
                type = "string",
                description = "Optional TimeZone ID, e.g. 'UTC', 'Asia/Kolkata', 'America/New_York'",
                required = false,
                default = "UTC"
            )
        ),
        permissionLevel = PermissionLevel.SAFE,
        sideEffects = SideEffectType.NONE,
        idempotent = false,
        status = CapabilityStatus.IMPLEMENTED
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val startTime = System.currentTimeMillis()
        val tzId = args["timezone"]?.toString() ?: "UTC"
        val tz = TimeZone.getTimeZone(tzId)

        val cal = Calendar.getInstance(tz)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = tz }
        val readableFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy HH:mm:ss z", Locale.US).apply { timeZone = tz }

        val json = JSONObject().apply {
            put("iso", isoFormat.format(cal.time))
            put("readable", readableFormat.format(cal.time))
            put("timezone", tz.id)
            put("epochMs", cal.timeInMillis)
            put("dayOfWeek", cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US))
        }

        return ToolResult(
            toolId = "datetime",
            success = true,
            data = json.toString(),
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }
}

/**
 * Text Processing Tool
 */
class TextProcessingTool : BaseTool {
    override val manifest = ToolManifest(
        id = "text_processing",
        name = "Text Processor",
        version = "1.0.0",
        description = "Provides word count, character count, casing transformation, whitespace cleaning, and regex extraction.",
        category = "Utility",
        inputParameters = listOf(
            ToolParameter("text", "string", "Text to process"),
            ToolParameter("operation", "string", "Operation: 'stats', 'uppercase', 'lowercase', 'titlecase', 'clean_whitespace', 'extract_regex'", required = true),
            ToolParameter("regexPattern", "string", "Pattern for regex extraction (only if operation is extract_regex)", required = false)
        ),
        permissionLevel = PermissionLevel.SAFE,
        sideEffects = SideEffectType.NONE,
        idempotent = true,
        status = CapabilityStatus.IMPLEMENTED
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val startTime = System.currentTimeMillis()
        val text = args["text"]?.toString() ?: ""
        val op = args["operation"]?.toString()?.lowercase() ?: "stats"

        val resultJson = JSONObject()
        when (op) {
            "stats" -> {
                val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                resultJson.put("characterCount", text.length)
                resultJson.put("wordCount", words.size)
                resultJson.put("lineCount", text.lines().size)
            }
            "uppercase" -> resultJson.put("result", text.uppercase())
            "lowercase" -> resultJson.put("result", text.lowercase())
            "titlecase" -> {
                val words = text.split(" ").map { it.replaceFirstChar { c -> c.uppercase() } }
                resultJson.put("result", words.joinToString(" "))
            }
            "clean_whitespace" -> {
                resultJson.put("result", text.trim().replace("\\s+".toRegex(), " "))
            }
            "extract_regex" -> {
                val pattern = args["regexPattern"]?.toString() ?: ".*"
                val matches = Regex(pattern).findAll(text).map { it.value }.toList()
                resultJson.put("matches", JSONArray(matches))
            }
            else -> {
                return ToolResult("text_processing", false, "Unknown operation: $op", 0L)
            }
        }

        return ToolResult(
            toolId = "text_processing",
            success = true,
            data = resultJson.toString(),
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }
}

/**
 * JSON Processing Tool
 */
class JsonProcessingTool : BaseTool {
    override val manifest = ToolManifest(
        id = "json_processing",
        name = "JSON Processor",
        version = "1.0.0",
        description = "Validates JSON structure, formats/prettifies JSON, and queries keys/paths.",
        category = "Utility",
        inputParameters = listOf(
            ToolParameter("jsonString", "string", "JSON payload string"),
            ToolParameter("operation", "string", "Operation: 'validate', 'format', 'keys'", required = true)
        ),
        permissionLevel = PermissionLevel.SAFE,
        sideEffects = SideEffectType.NONE,
        idempotent = true,
        status = CapabilityStatus.IMPLEMENTED
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        val startTime = System.currentTimeMillis()
        val raw = args["jsonString"]?.toString() ?: ""
        val op = args["operation"]?.toString()?.lowercase() ?: "validate"

        return try {
            val output = JSONObject()
            when (op) {
                "validate" -> {
                    try {
                        JSONObject(raw)
                        output.put("valid", true)
                        output.put("type", "object")
                    } catch (_: Exception) {
                        JSONArray(raw)
                        output.put("valid", true)
                        output.put("type", "array")
                    }
                }
                "format" -> {
                    val formatted = try {
                        JSONObject(raw).toString(2)
                    } catch (_: Exception) {
                        JSONArray(raw).toString(2)
                    }
                    output.put("formatted", formatted)
                }
                "keys" -> {
                    val obj = JSONObject(raw)
                    val keys = obj.keys().asSequence().toList()
                    output.put("keys", JSONArray(keys))
                }
                else -> throw IllegalArgumentException("Unknown operation: $op")
            }
            ToolResult("json_processing", true, output.toString(), System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            ToolResult("json_processing", false, "Invalid JSON: ${e.message}", System.currentTimeMillis() - startTime)
        }
    }
}

/**
 * Placeholder Tools for Approval / Restriction / Extension Demonstrations
 */

class WebSearchTool : BaseTool {
    override val manifest = ToolManifest(
        id = "web_search",
        name = "Web Search Engine",
        version = "1.0.0",
        description = "Searches the live public web for current news, facts, and documentation.",
        category = "Information",
        inputParameters = listOf(ToolParameter("query", "string", "Search query terms")),
        permissionLevel = PermissionLevel.SAFE,
        sideEffects = SideEffectType.EXTERNAL,
        status = CapabilityStatus.NOT_CONFIGURED // Section 4 label
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        return ToolResult.notConfigured("web_search", "External Web Search API credentials not configured.")
    }
}

class FileOperationTool : BaseTool {
    override val manifest = ToolManifest(
        id = "file_operation",
        name = "Filesystem Operation",
        version = "1.0.0",
        description = "Reads, writes, or modifies host files. Sandboxed access only.",
        category = "System",
        inputParameters = listOf(
            ToolParameter("path", "string", "Relative target path"),
            ToolParameter("action", "string", "read | write | list")
        ),
        permissionLevel = PermissionLevel.CONFIRMATION_REQUIRED,
        sideEffects = SideEffectType.WRITE,
        status = CapabilityStatus.PLACEHOLDER
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        return ToolResult.notImplemented("file_operation", "Filesystem tool is a secure PLACEHOLDER stub; write operations disabled.")
    }
}

class SendEmailTool : BaseTool {
    override val manifest = ToolManifest(
        id = "send_email",
        name = "Email Dispatcher",
        version = "1.0.0",
        description = "Sends an email message to a specified recipient. Demonstrates confirmation required approval flow.",
        category = "Communication",
        inputParameters = listOf(
            ToolParameter("to", "string", "Recipient email address"),
            ToolParameter("subject", "string", "Email subject"),
            ToolParameter("body", "string", "Message body content")
        ),
        permissionLevel = PermissionLevel.CONFIRMATION_REQUIRED,
        sideEffects = SideEffectType.EXTERNAL,
        status = CapabilityStatus.PLACEHOLDER
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        // Section 4: "demo of the approval flow only; no real sending"
        return ToolResult(
            toolId = "send_email",
            success = true,
            data = "{\"status\": \"DISPATCH_SIMULATED\", \"message\": \"Email approval workflow passed. Actual SMTP dispatch disabled in demo environment.\"}",
            executionTimeMs = 120L,
            status = CapabilityStatus.PLACEHOLDER
        )
    }
}

class DeleteDataTool : BaseTool {
    override val manifest = ToolManifest(
        id = "delete_data",
        name = "Purge / Delete Data",
        version = "1.0.0",
        description = "Permanently deletes user or system records. Demonstrates restricted blocked flow.",
        category = "Security",
        inputParameters = listOf(
            ToolParameter("recordId", "string", "Identifier of the record to purge")
        ),
        permissionLevel = PermissionLevel.RESTRICTED,
        sideEffects = SideEffectType.WRITE,
        status = CapabilityStatus.PLACEHOLDER
    )

    override suspend fun execute(args: Map<String, Any?>): ToolResult {
        return ToolResult.notImplemented("delete_data", "Operation blocked: Data deletion requires server-side admin policy override.")
    }
}
