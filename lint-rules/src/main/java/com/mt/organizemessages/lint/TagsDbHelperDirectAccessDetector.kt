package com.mt.organizemessages.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression

/**
 * Lint rule that flags direct construction or static access of [TagsDbHelper]
 * from any package other than `com.mt.organizemessages.data`.
 *
 * **Rationale (ADR-001, ADR-002):**
 * - TagsDbHelper is a singleton — constructing it bypasses the singleton contract
 * - All data access must go through MessageRepository
 *
 * **What it catches:**
 * - `TagsDbHelper(context)` — direct constructor call
 * - `TagsDbHelper.getInstance(context)` — singleton access outside `data/`
 *
 * **Suppression:** `@Suppress("TagsDbHelperDirectAccess")` (use sparingly).
 */
class TagsDbHelperDirectAccessDetector : Detector(), Detector.UastScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "TagsDbHelperDirectAccess",
            briefDescription = "Direct TagsDbHelper access outside data layer",
            explanation = """
                TagsDbHelper must only be accessed through MessageRepository (ADR-001).
                Calling TagsDbHelper directly from the UI layer:
                - Bypasses the singleton contract (ADR-002), risking connection leaks
                - Makes the component untestable (can't inject a mock)
                - Breaks the layered architecture

                **Fix:** Add the operation you need as a method on MessageRepository and call it there.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                TagsDbHelperDirectAccessDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val TAGSDBHELPER_SIMPLE_NAME = "TagsDbHelper"
        private const val ALLOWED_PACKAGE = "com.mt.organizemessages.data"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java, USimpleNameReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {

            /** Catches constructor calls: `TagsDbHelper(context)` */
            override fun visitCallExpression(node: UCallExpression) {
                if (node.classReference?.resolvedName == TAGSDBHELPER_SIMPLE_NAME) {
                    val callerPackage = context.uastFile?.packageName ?: return
                    if (!callerPackage.startsWith(ALLOWED_PACKAGE)) {
                        context.report(
                            issue = ISSUE,
                            scope = node,
                            location = context.getLocation(node),
                            message = "Direct `TagsDbHelper` constructor call detected. " +
                                "Use `MessageRepository` instead. See ADR-001."
                        )
                    }
                }
            }

            /** Catches static method calls: `TagsDbHelper.getInstance(context)` */
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                if (node.identifier == TAGSDBHELPER_SIMPLE_NAME) {
                    val callerPackage = context.uastFile?.packageName ?: return
                    if (!callerPackage.startsWith(ALLOWED_PACKAGE)) {
                        // Only flag if this looks like a method/property access (not an import)
                        val parent = node.uastParent
                        if (parent is UCallExpression || parent?.javaClass?.name?.contains("Qualified") == true) {
                            context.report(
                                issue = ISSUE,
                                scope = node,
                                location = context.getLocation(node),
                                message = "Direct `TagsDbHelper` access detected. " +
                                    "Use `MessageRepository` instead. See ADR-001."
                            )
                        }
                    }
                }
            }
        }
}
