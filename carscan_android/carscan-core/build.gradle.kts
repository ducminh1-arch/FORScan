plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.carscan.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.mockito:mockito-core:5.11.0")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

/*
 * Workaround for AGP ExtractAnnotations detached configuration:
 * 1. Root cause: AGP's ExtractAnnotations task uses a detached configuration containing
 *    com.android.tools.external.com-intellij:kotlin-compiler:31.3.0 (~50MB) and
 *    intellij-core:31.3.0 (~32MB). In offline or throttled network environments,
 *    downloading these jars from dl.google.com times out and blocks the build.
 * 2. Why typedefs.txt stub: Downstream task SyncLibJars (syncDebugLibJars / syncReleaseLibJars)
 *    declares @InputFile typedefFile pointing to annotations_typedef_file/<variant>/<taskName>/typedefs.txt.
 *    Because typedefFile is non-optional, simply setting extract*Annotations.enabled = false causes
 *    SyncLibJars to fail with "File specified for property 'typedefFile' does not exist".
 * 3. Pre-creating an empty typedefs.txt satisfies SyncLibJars' non-optional @InputFile check,
 *    allowing bundleAar and assembleDebug to succeed reliably without external network dependencies.
 */
tasks.matching { it.name.startsWith("extract") && it.name.endsWith("Annotations") }.configureEach {
    enabled = false
}

tasks.named("preBuild").configure {
    doLast {
        listOf("debug", "release").forEach { variant ->
            val cap = variant.replaceFirstChar { it.uppercase() }
            val taskName = "extract${cap}Annotations"
            val typedefFile = layout.buildDirectory.file("intermediates/annotations_typedef_file/$variant/$taskName/typedefs.txt").get().asFile
            typedefFile.parentFile.mkdirs()
            if (!typedefFile.exists()) {
                typedefFile.writeText("")
            }
        }
    }
}

