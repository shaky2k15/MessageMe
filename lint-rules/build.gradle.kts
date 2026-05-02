plugins {
    id("kotlin")
    id("com.android.lint")
}

// Lint rules module — produces a lint.jar consumed by the :app module
// Android Lint SDK requires Java/Kotlin only, no Android dependencies

dependencies {
    // Lint API — matches the Android Gradle Plugin version
    compileOnly("com.android.tools.lint:lint-api:31.10.0")
    compileOnly("com.android.tools.lint:lint-checks:31.10.0")
}

// Register the LintRegistry so AGP discovers our custom rules
tasks.withType<Jar> {
    manifest {
        attributes["Lint-Registry-v2"] = "com.mt.organizemessages.lint.MessageMeLintRegistry"
    }
}

