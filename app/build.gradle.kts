import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// 普通开发与未签名候选不读取正式配置；签名材料始终在公开源码之外。
val unsignedRelease = providers.gradleProperty("unsignedRelease").map(String::toBoolean).getOrElse(false)
val officialRelease = providers.gradleProperty("officialRelease").map(String::toBoolean).getOrElse(false)
check(!(unsignedRelease && officialRelease)) { "Choose either an unsigned candidate or an official signed release." }
val keystoreProps = Properties().apply {
    if (officialRelease) {
        val f = rootProject.file(System.getenv("KAIFANLA_SIGNING_PROPERTIES") ?: "keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
}
if (officialRelease) {
    check(listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { !keystoreProps.getProperty(it).isNullOrBlank() }) {
        "Original signing configuration missing or incomplete; official release refused."
    }
    try {
        val file = rootProject.file(keystoreProps.getProperty("storeFile").removePrefix("../"))
        val store = KeyStore.getInstance(file, keystoreProps.getProperty("storePassword").toCharArray())
        val alias = keystoreProps.getProperty("keyAlias")
        check(store.getKey(alias, keystoreProps.getProperty("keyPassword").toCharArray()) is PrivateKey)
        val digest = MessageDigest.getInstance("SHA-256").digest(store.getCertificate(alias).encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(digest == "cd1c51e824a2f3db38f85ae8bcb98b84a3b8024f0840f1c5dcab3072a9bb4e4d")
    } catch (_: Exception) {
        throw GradleException("Original signing identity mismatch or private key inaccessible; official release refused.")
    }
}

android {
    namespace = "com.my.firstapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jintianchishenme.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "2.4"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile").removePrefix("../"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 测试版和正式版可以同时装在一台手机上
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "开饭啦·测试")
            // 本地测试用的 MuMu 模拟器是 x86_64，测试版留着它；真机用不到。
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
            resValue("string", "app_name", "开饭啦")
            // 中文 OCR 的 native 库每种架构要 6~11 MB。正式版只保留真机在用的两种 ARM 架构，
            // 不带 x86/x86_64，安装包能从约 44 MB 降到约 22 MB。
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric 下载的 Android 测试运行时复用 Gradle 缓存，遵循本机 D 盘设置。
            it.systemProperty("user.home", gradle.gradleUserHomeDir.absolutePath)
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            )
        }
    }

    buildFeatures {
        compose = true
        resValues = true
    }
}

gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { it.project == project && it.name in setOf("assembleRelease", "packageRelease", "bundleRelease") }
    if (releaseRequested && !officialRelease && !unsignedRelease) {
        throw GradleException("Official release requires -PofficialRelease=true and the original signer. Use -PunsignedRelease=true only for a non-installable unsigned candidate.")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Google 官方表情选择器：店铺图标「更多表情」
    implementation(libs.androidx.emoji2.emojipicker)
    // 店铺封面照片的加载与显示（#29）
    implementation(libs.coil.compose)
    // 截图识别菜单：bundled 中文 OCR。模型和 native 库都打进 APK，
    // 离线可用、不需要设备装 Google Play 服务（国内手机能直接用）。
    implementation(libs.mlkit.text.recognition.chinese)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    // 单元测试在电脑上跑，安卓自带的 org.json 只是空壳，换成真的
    testImplementation(libs.org.json)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "1g"
    systemProperty("user.home", rootProject.file(".gradle/task-home").absolutePath)
    listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach {
        System.getProperty(it)?.let { value -> systemProperty(it, value) }
    }
}

// Optional native Compose screenshot/interaction suite; no shipping dependencies.
if (providers.gradleProperty("mealUiEvidence").orNull == "true") {
    android.sourceSets.getByName("test").kotlin.srcDir(rootProject.file("tools/ui-snapshots/flow"))
    // Isolated host rendering experiment; never part of the shipping application.
    android.testOptions.unitTests.apply {
        isIncludeAndroidResources = true
        all {
            it.maxHeapSize = "1g"
            it.systemProperty("user.home", rootProject.file(".gradle/task-home").also { it.mkdirs() }.absolutePath)
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
                System.getProperty(key)?.let { value -> it.systemProperty(key, value) }
            }
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            )
        }
    }
    dependencies {
        testImplementation(platform("androidx.compose:compose-bom:2026.03.01"))
        testImplementation("androidx.compose.ui:ui-test-junit4")
        debugImplementation("androidx.compose.ui:ui-test-manifest")
        testImplementation("org.robolectric:robolectric:4.17")
        testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
        testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
    }
}
