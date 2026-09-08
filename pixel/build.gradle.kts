import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "au.pointandyshoot.picquery"
    compileSdk = 36
    defaultConfig {
        applicationId = "au.pointandyshoot.picquery"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    androidResources { noCompress += listOf("tflite") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = false } }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.ai.edge.litert:litert:2.2.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
}

tasks.withType<Test>().configureEach {
    for (key in listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore")) {
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}

val verifyModelAssets by tasks.registering {
    doLast {
        if (providers.gradleProperty("allowMissingModels").orNull == "true") return@doLast
        val assets = file("src/main/assets")
        val idFile = assets.resolve("model-id.txt")
        check(idFile.isFile) { "Run python scripts/models/prepare_models.py first (or -PallowMissingModels=true for UI/OCR-only builds)." }
        val parts = idFile.readText().trim().split(':')
        check(parts.size == 3 && parts[0] == "MobileCLIP2-S0") { "Unexpected model identity." }
        for ((index, name) in listOf("image_model.tflite", "text_model.tflite").withIndex()) {
            val model = assets.resolve(name)
            check(model.isFile) { "Missing $name. Run scripts/models/prepare_models.py." }
            val digest = MessageDigest.getInstance("SHA-256")
            model.inputStream().use { stream ->
                val buffer = ByteArray(1024 * 1024)
                while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == parts[index + 1]) { "Checksum mismatch: $name" }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyModelAssets) }
