import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)


    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.recall_ai"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.recall_ai"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
// Safely extract the keys. Fallback to empty string if missing so the build doesn't crash.
        // Safely extract the keys. Fallback to empty string if missing so the build doesn't crash.
        val geminiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
        val openAiKey = localProperties.getProperty("OPENAI_API_KEY") ?: ""

        // Generate the BuildConfig fields. Notice the escaped quotes "\"$variable\""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0") // MVVM

    // Coroutines & Flow [cite: 86]
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Hilt (Dependency Injection)
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")

    // Room (Local Database)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Retrofit (Network/API)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // WorkManager (For background generation & termination)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
// Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.7.x")

// Hilt + Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.1.x")

// Lifecycle (collectAsStateWithLifecycle)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.x")

// Activity Compose (enableEdgeToEdge, setContent)
    implementation("androidx.activity:activity-compose:1.8.x")

    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    kapt("androidx.hilt:hilt-compiler:1.1.0")

}