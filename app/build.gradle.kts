plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.autowallpaper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.autowallpaper"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // OkHttp - 自动跟随302重定向
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson - JSON 解析
    implementation("com.google.code.gson:gson:2.10.1")

    // Glide - 图片加载
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // WorkManager - 系统级定时调度（省电，无常驻进程）
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
