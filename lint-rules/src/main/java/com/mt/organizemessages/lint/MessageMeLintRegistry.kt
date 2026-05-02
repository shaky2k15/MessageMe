package com.mt.organizemessages.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

/**
 * Registers all custom MessageMe lint rules.
 *
 * New rules must be added to [issues] here. AGP discovers this registry
 * via the `Lint-Registry-v2` manifest attribute in the :lint-rules JAR.
 */
class MessageMeLintRegistry : IssueRegistry() {

    override val issues = listOf(
        TagsDbHelperDirectAccessDetector.ISSUE,
        MainThreadIoDetector.ISSUE
    )

    override val api: Int = CURRENT_API

    override val vendor = Vendor(
        vendorName = "MessageMe",
        identifier = "com.mt.organizemessages",
        feedbackUrl = "https://github.com/your-org/MessageMe/issues"
    )
}
