package dev.sausage.runtime

import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteProgram
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import java.util.Locale

internal class SausageDatabaseBridge(
    context: Context,
    storageScope: String,
) : AutoCloseable {
    private val helper = SausageDatabaseHelper(
        context.applicationContext,
        "sausage.database.${storageScope.sha256()}.db",
    )

    @JavascriptInterface
    fun execute(
        sql: String,
        encodedParameters: String,
    ): String = response {
        val statementKind = sql.requireStatement(EXECUTE_STATEMENTS)
        val parameters = encodedParameters.decodeParameters()
        val database = helper.writableDatabase

        database.compileStatement(sql).use { statement ->
            statement.bindParameters(parameters)
            statement.execute()

            val changes = if (statementKind in CHANGING_STATEMENTS) {
                DatabaseUtils.longForQuery(database, "SELECT changes()", null)
            } else {
                0
            }
            val lastInsertId = if (statementKind in INSERT_STATEMENTS && changes > 0) {
                DatabaseUtils.longForQuery(database, "SELECT last_insert_rowid()", null)
                    .requireSafeJavaScriptInteger()
            } else {
                null
            }

            JSONObject()
                .put("changes", changes.requireSafeJavaScriptInteger())
                .put("lastInsertId", lastInsertId ?: JSONObject.NULL)
        }
    }

    @JavascriptInterface
    fun query(
        sql: String,
        encodedParameters: String,
    ): String = response {
        sql.requireStatement(QUERY_STATEMENTS)
        val parameters = encodedParameters.decodeParameters()
        val database = helper.readableDatabase
        val cursorFactory = SQLiteDatabase.CursorFactory { _, driver, editTable, query ->
            query.bindParameters(parameters)
            SQLiteCursor(driver, editTable, query)
        }

        database.rawQueryWithFactory(cursorFactory, sql, emptyArray(), "").use { cursor ->
            cursor.toJsonRows()
        }
    }

    override fun close() {
        helper.close()
    }

    private fun Cursor.toJsonRows(): JSONArray {
        val names = columnNames
        if (names.toSet().size != names.size) {
            throw IllegalArgumentException("Database query column names must be unique.")
        }

        val rows = JSONArray()
        while (moveToNext()) {
            if (rows.length() >= MAX_RESULT_ROWS) {
                throw IllegalArgumentException("Database queries may return at most $MAX_RESULT_ROWS rows.")
            }

            val row = JSONObject()
            names.forEachIndexed { index, name ->
                val value = when (getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                    Cursor.FIELD_TYPE_INTEGER -> getLong(index).requireSafeJavaScriptInteger()
                    Cursor.FIELD_TYPE_FLOAT -> getDouble(index).also { number ->
                        if (!number.isFinite()) {
                            throw IllegalArgumentException("Database queries cannot return non-finite numbers.")
                        }
                    }
                    Cursor.FIELD_TYPE_STRING -> getString(index)
                    Cursor.FIELD_TYPE_BLOB -> throw IllegalArgumentException(
                        "Binary database values are not supported by this runtime slice.",
                    )
                    else -> throw IllegalArgumentException("Database query returned an unsupported value.")
                }
                row.put(name, value)
            }
            rows.put(row)

            if (rows.toString().toByteArray(Charsets.UTF_8).size > MAX_RESULT_BYTES) {
                throw IllegalArgumentException("Database query results may not exceed 512 KB.")
            }
        }
        return rows
    }

    private fun String.requireStatement(allowedStatements: Set<String>): String {
        if (toByteArray(Charsets.UTF_8).size > MAX_SQL_BYTES) {
            throw IllegalArgumentException("Database statements may not exceed 32 KB.")
        }
        val statement = trim()
        if (statement.isEmpty()) {
            throw IllegalArgumentException("Database statements may not be empty.")
        }
        if (';' in statement) {
            throw IllegalArgumentException("Database calls accept exactly one statement without a semicolon.")
        }
        if ("--" in statement || "/*" in statement || "*/" in statement) {
            throw IllegalArgumentException("SQL comments are not supported in database calls.")
        }

        val kind = statement
            .takeWhile(Char::isLetter)
            .uppercase(Locale.ROOT)
        if (kind !in allowedStatements) {
            throw IllegalArgumentException("The $kind statement is not supported by this database call.")
        }
        return kind
    }

    private fun String.decodeParameters(): List<Any?> {
        if (toByteArray(Charsets.UTF_8).size > MAX_PARAMETER_BYTES) {
            throw IllegalArgumentException("Database parameters may not exceed 256 KB.")
        }

        val parser = JSONTokener(this)
        val parsed = try {
            parser.nextValue()
        } catch (error: Exception) {
            throw IllegalArgumentException("Database parameters must be a JSON array.", error)
        }
        if (parsed !is JSONArray || parser.nextClean() != END_OF_JSON) {
            throw IllegalArgumentException("Database parameters must be a JSON array.")
        }
        if (parsed.length() > MAX_PARAMETERS) {
            throw IllegalArgumentException("Database calls may use at most $MAX_PARAMETERS parameters.")
        }

        return List(parsed.length()) { index ->
            when (val value = parsed.get(index)) {
                JSONObject.NULL -> null
                is String -> {
                    if (value.toByteArray(Charsets.UTF_8).size > MAX_PARAMETER_STRING_BYTES) {
                        throw IllegalArgumentException("A database string parameter may not exceed 64 KB.")
                    }
                    value
                }
                is Boolean -> value
                is Byte, is Short, is Int, is Long -> (value as Number).toLong()
                is Float, is Double -> (value as Number).toDouble().also { number ->
                    if (!number.isFinite()) {
                        throw IllegalArgumentException("Database parameters must contain finite numbers.")
                    }
                }
                else -> throw IllegalArgumentException(
                    "Database parameters may contain only null, Boolean, number or string values.",
                )
            }
        }
    }

    private fun SQLiteProgram.bindParameters(parameters: List<Any?>) {
        parameters.forEachIndexed { index, value ->
            val parameterIndex = index + 1
            when (value) {
                null -> bindNull(parameterIndex)
                is String -> bindString(parameterIndex, value)
                is Boolean -> bindLong(parameterIndex, if (value) 1 else 0)
                is Long -> bindLong(parameterIndex, value)
                is Double -> bindDouble(parameterIndex, value)
                else -> error("Unsupported database parameter type")
            }
        }
    }

    private fun Long.requireSafeJavaScriptInteger(): Long {
        if (this !in -MAX_SAFE_JAVASCRIPT_INTEGER..MAX_SAFE_JAVASCRIPT_INTEGER) {
            throw IllegalArgumentException("Database integer is outside JavaScript's safe range.")
        }
        return this
    }

    private fun response(block: () -> Any): String = try {
        JSONObject()
            .put("ok", true)
            .put("value", block())
            .toString()
    } catch (error: Exception) {
        JSONObject()
            .put("ok", false)
            .put("error", error.message?.take(MAX_ERROR_LENGTH) ?: "The database operation failed.")
            .toString()
    }

    private fun String.sha256(): String = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    companion object {
        const val JAVASCRIPT_NAME = "__sausageDatabase"

        private val EXECUTE_STATEMENTS = setOf(
            "ALTER",
            "CREATE",
            "DELETE",
            "DROP",
            "INSERT",
            "REPLACE",
            "UPDATE",
        )
        private val INSERT_STATEMENTS = setOf("INSERT", "REPLACE")
        private val CHANGING_STATEMENTS = setOf("DELETE", "INSERT", "REPLACE", "UPDATE")
        private val QUERY_STATEMENTS = setOf("SELECT")
        private const val MAX_SAFE_JAVASCRIPT_INTEGER = 9_007_199_254_740_991L
        private const val MAX_SQL_BYTES = 32 * 1024
        private const val MAX_PARAMETER_BYTES = 256 * 1024
        private const val MAX_PARAMETER_STRING_BYTES = 64 * 1024
        private const val MAX_PARAMETERS = 100
        private const val MAX_RESULT_ROWS = 500
        private const val MAX_RESULT_BYTES = 512 * 1024
        private const val MAX_ERROR_LENGTH = 300
        private const val END_OF_JSON = '\u0000'
    }
}

private class SausageDatabaseHelper(
    context: Context,
    name: String,
) : SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {
    override fun onCreate(database: SQLiteDatabase) = Unit

    override fun onUpgrade(
        database: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) = Unit

    companion object {
        private const val DATABASE_VERSION = 1
    }
}
