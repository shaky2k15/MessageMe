package com.mt.organizemessages.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

/**
 * Lint rule that flags `ContentResolver.query()` and SQLite database access
 * (`readableDatabase`, `writableDatabase`) when called directly inside a
 * `@Composable` function or a `LaunchedEffect` lambda without a
 * `withContext(Dispatchers.IO)` wrapper.
 *
 * **Rationale:**
 * Running blocking I/O on the Main thread causes ANRs when the message
 * database is large. All such calls must be dispatched to `Dispatchers.IO`.
 *
 * **What it catches:**
 * - `contentResolver.query(...)` inside a composable scope
 * - `db.readableDatabase` / `db.writableDatabase` outside `Dispatchers.IO`
 *
 * **Suppression:** `@Suppress("MainThreadIo")` with a comment explaining why.
 */
class MainThreadIoDetector : Detector(), Detector.UastScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "MainThreadIo",
            briefDescription = "Blocking I/O on the Main thread",
            explanation = """
                ContentResolver.query(), readableDatabase, and writableDatabase are blocking calls.
                Calling them on the Main thread causes ANRs when the SMS database is large.

                **Fix:** Wrap the call in `withContext(Dispatchers.IO)` inside a coroutine,
                or move the logic into a ViewModel that launches on `Dispatchers.IO`.

                See ADR-003 and InboxViewModel for the correct pattern.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                MainThreadIoDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val IO_BLOCKING_METHODS = setOf(
            "query", "readableDatabase", "writableDatabase"
        )

        private val IO_SAFE_CONTEXTS = setOf(
            "withContext", "launch", "async"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val methodName = node.methodName ?: return
                if (methodName !in IO_BLOCKING_METHODS) return

                // Walk up the AST to check if this call is inside a @Composable function
                // or inside a LaunchedEffect without a withContext wrapper
                var parent: UElement? = node.uastParent
                var insideComposable = false
                var hasIoContext = false

                while (parent != null) {
                    // Check if we're inside withContext(Dispatchers.IO)
                    if (parent is UCallExpression && parent.methodName in IO_SAFE_CONTEXTS) {
                        val args = parent.valueArguments
                        if (args.any { it.asSourceString().contains("IO") }) {
                            hasIoContext = true
                            break
                        }
                    }

                    // Check if we're inside a @Composable function
                    if (parent is UMethod) {
                        val annotations = parent.annotations
                        if (annotations.any { it.qualifiedName?.contains("Composable") == true }) {
                            insideComposable = true
                        }
                        break
                    }

                    parent = parent.uastParent
                }

                if (insideComposable && !hasIoContext) {
                    context.report(
                        issue = ISSUE,
                        scope = node,
                        location = context.getLocation(node),
                        message = "`$methodName` is a blocking I/O call inside a @Composable. " +
                            "Move this to a ViewModel using `withContext(Dispatchers.IO)`. See ADR-003."
                    )
                }
            }
        }
}
