
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Random

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
var isCIBuild: Boolean = System.getenv("CI").toBoolean()
// 随机字符串和数字
fun generateRandomString(length: Int): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    val random = Random()
    return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
}

//isCIBuild = true // 没有c++源码时开启CI构建, push前关闭

android {
    namespace = "fansirsqi.xposed.sesame"
    compileSdk = 36
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
//    val gitCommitCount: Int = runCatching {
//        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
//                .redirectErrorStream(true)
//                .start()
//        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
//        output.toInt()
//    }.getOrElse {
//        println("获取 git 提交数失败: ${it.message}")
//        1
//    }
    defaultConfig {
        vectorDrawables.useSupportLibrary = true
        applicationId = "fansirsqi.xposed.sesame"
        minSdk = 24
        targetSdk = 36

        if (!isCIBuild) {
            ndk {
                abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
            }
        }


        val buildDate = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
        }.format(Date())

        val buildTime = SimpleDateFormat("HH:mm:ss", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
        }.format(Date())

        val buildTargetCode = try {
//            buildDate.replace("-", ".") + "." + buildTime.replace(":", ".")
            buildDate.replace("-", ".")
        } catch (_: Exception) {
            "0000"
        }


        val versionNumber = "0.4.4"
        val dateString = SimpleDateFormat("yyMMdd", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("GMT+8")
        }.format(Date())
        versionCode = 30
        val buildTag = "beta"
        versionName = "$versionNumber-$dateString"

        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        buildConfigField("String", "BUILD_NUMBER", "\"$buildTargetCode\"")
        buildConfigField("String", "BUILD_TAG", "\"$buildTag\"")
        buildConfigField("String", "VERSION", "\"$versionName\"")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        testOptions {
            unitTests.all {
                it.enabled = false
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }



    flavorDimensions += "default"
    productFlavors {
        create("normal") {
            dimension = "default"
            extra.set("applicationType", "Normal")
        }
        create("compatible") {
            dimension = "default"
            extra.set("applicationType", "Compatible")
        }
    }
    compileOptions {
        // 全局默认设置
        isCoreLibraryDesugaringEnabled = true // 启用脱糖
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    productFlavors.all {
        when (name) {
            "normal" -> {
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlin {
                    compilerOptions {
                        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
                    }
                }
            }

            "compatible" -> {
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
                kotlin {
                    compilerOptions {
                        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
                    }
                }
            }
        }
    }

    signingConfigs {
        getByName("debug") {
        }
        val keyFile = rootProject.file("key/ycKey.jks")
        val envStorePass = System.getenv("ANDROID_SIGNING_PASSWORD")
        val envKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
        val envKeyPass = System.getenv("ANDROID_KEY_PASSWORD")
        if (keyFile.exists() && !envStorePass.isNullOrBlank() && !envKeyAlias.isNullOrBlank() && !envKeyPass.isNullOrBlank()) {
            create("release") {
                storeFile = keyFile
                storePassword = envStorePass
                keyAlias = envKeyAlias
                keyPassword = envKeyPass
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            versionNameSuffix = "-debug"
            isShrinkResources = false
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    val cmakeFile = file("src/main/cpp/CMakeLists.txt")
    if (!isCIBuild && cmakeFile.exists()) {
        externalNativeBuild {
            cmake {
                path = cmakeFile
                version = "3.31.6"
                ndkVersion = "29.0.13113456"
            }
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val flavorName = variant.flavorName.replaceFirstChar { it.uppercase() }
            val fileName = "Sesame-TK-$flavorName-${variant.versionName}.apk"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = fileName
        }
    }
}
dependencies {
    // Updater 模块接入
    implementation(project(":updater"))

    // Shizuku
    implementation(libs.rikka.shizuku.api)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Coil for image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.nanohttpd)

    implementation(libs.androidx.constraintlayout)

    implementation(libs.activity.compose)

    implementation(libs.core.ktx)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j.api)
    implementation(libs.logback.android)
    implementation(libs.appcompat)
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)
    implementation(libs.material)
    implementation(libs.webkit)

    compileOnly(files("libs/api-82.jar"))

    compileOnly(files("libs/api-100.aar"))
    implementation(files("libs/service-100-1.0.0.aar"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.okhttp)
    implementation(libs.dexkit)
    implementation(libs.jackson.kotlin)

    implementation(libs.mmkv)
    implementation(libs.zip4j)
    implementation(libs.documentfile)

    coreLibraryDesugaring(libs.desugar)

    add("normalImplementation", libs.jackson.core)
    add("normalImplementation", libs.jackson.databind)
    add("normalImplementation", libs.jackson.annotations)

    add("compatibleImplementation", libs.jackson.core.compatible)
    add("compatibleImplementation", libs.jackson.databind.compatible)
    add("compatibleImplementation", libs.jackson.annotations.compatible)

}