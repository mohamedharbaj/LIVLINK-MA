plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "ma.livlink.app" // غيّره قبل أول نشر على Play إلا بغيتي اسم آخر (ما كيتبدلش من بعد)
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = (project.findProperty("KEYSTORE_FILE") as? String)
                ?: System.getenv("KEYSTORE_FILE")

            if (!keystoreFile.isNullOrBlank()) {
                val candidateFile = when {
                    file(keystoreFile).exists() -> file(keystoreFile)
                    file("${rootDir}/$keystoreFile").exists() -> file("${rootDir}/$keystoreFile")
                    file("${projectDir}/$keystoreFile").exists() -> file("${projectDir}/$keystoreFile")
                    else -> null
                }
                if (candidateFile != null) {
                    storeFile = candidateFile
                    storePassword = (project.findProperty("KEYSTORE_PASSWORD") as? String)?.takeIf { it.isNotBlank() }
                        ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
                    keyAlias = (project.findProperty("KEY_ALIAS") as? String)?.takeIf { it.isNotBlank() }
                        ?: System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
                        ?: "upload"
                    keyPassword = (project.findProperty("KEY_PASSWORD") as? String)?.takeIf { it.isNotBlank() }
                        ?: System.getenv("KEY_PASSWORD") ?: ""
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            }
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Firebase: تسجيل دخول مجهول + قاعدة بيانات الإعلانات
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // App Check: يحمي Firestore من السكريبتات والحسابات الوهمية (التفعيل الإجباري من Firebase Console)
    implementation(libs.firebase.appcheck.playintegrity)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

gradle.taskGraph.whenReady {
    val isReleaseBuildRequested = allTasks.any { task ->
        task.name.contains("Release", ignoreCase = true) &&
        (task.name.startsWith("assemble") || task.name.startsWith("bundle") || task.name.startsWith("package"))
    }
    if (isReleaseBuildRequested) {
        val releaseConfig = android.signingConfigs.getByName("release")
        if (releaseConfig.storeFile == null || !releaseConfig.storeFile!!.exists()) {
            throw GradleException(
                """
                |========================================================================================
                |[LIVLINK BUILD ERROR] فشل بناء نسخة Release: ملف مفتاح التوقيع (Keystore) غير متوفر!
                |----------------------------------------------------------------------------------------
                |لإصدار نسخة Release صالحة وموقعة رسمياً، يجب توفير الأسرار والمتغيرات التالية:
                |  - KEYSTORE_BASE64: كود Base64 لملف الـ Keystore (أو تعيين مسار KEYSTORE_FILE).
                |  - KEYSTORE_PASSWORD: كلمة مرور ملف المفاتيح.
                |  - KEY_ALIAS: الاسم التعريفي للمفتاح داخل الـ Keystore (الافتراضي: upload).
                |  - KEY_PASSWORD: كلمة مرور المفتاح الخاص.
                |
                |تم إيقاف بناء Release عمداً لمنع إنتاج APK غير موقّع أو موقّع بمفتاح Debug تجريبي.
                |ملاحظة: يمكنك بناء نسخة Debug بشكل مستقل ودون الحاجة لمفتاح التوقيع عبر:
                |  ./gradlew assembleDebug
                |========================================================================================
                """.trimMargin()
            )
        }
    }
}
