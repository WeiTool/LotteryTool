plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.lotterytool"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lotterytool"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.2"

        testInstrumentationRunner = "com.lotterytool.HiltTestRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug" // 这会让包名变成 com.lotterytool.debug
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testNamespace = "com.lotterytool.test"

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose 基础 (BOM 管理版本)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material)

    implementation(libs.retrofit) // 网络
    implementation(libs.converter.gson) // JSON
    implementation(libs.androidx.lifecycle.viewmodel.compose) // ViewModel
    implementation(libs.androidx.navigation.compose) // Navigation
    implementation(libs.coil.compose)
    implementation(libs.core)

    // Room 依赖
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.appcompat)
    ksp(libs.androidx.room.compiler) // 使用 KSP 进行编译处理

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    //WorkManager
    implementation (libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.hilt.compiler)
    // 预览工具
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt 测试依赖
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)

}
