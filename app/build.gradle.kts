import com.android.build.api.dsl.Packaging

plugins {
    id("com.google.gms.google-services")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.piero.deliveryearningtracker"
    compileSdk = 35
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
        }
    }
    defaultConfig {
        applicationId = "com.piero.deliveryearningtracker"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    androidResources {
        //generateLocaleConfig = true
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_ADS", "true")
            buildConfigField("String", "AD_UNIT_ID_BANNER", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "AD_UNIT_ID_PREMIO1", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "AD_UNIT_ID_PREMIO2", "\"ca-app-pub-3940256099942544/1033173717\"")
        }
        create("personal") {
            buildConfigField("boolean", "ENABLE_ADS", "false")
            buildConfigField("String", "AD_UNIT_ID_BANNER", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "AD_UNIT_ID_PREMIO1", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "AD_UNIT_ID_PREMIO2", "\"ca-app-pub-3940256099942544/1033173717\"")
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            buildConfigField("boolean", "ENABLE_ADS", "true")
            buildConfigField("String", "AD_UNIT_ID_BANNER", "\"ca-app-pub-4623148515468491/7100991744\"")
            buildConfigField("String", "AD_UNIT_ID_PREMIO1", "\"ca-app-pub-4623148515468491/8681698542\"")
            buildConfigField("String", "AD_UNIT_ID_PREMIO2", "\"ca-app-pub-4623148515468491/9354151382\"")
            isMinifyEnabled = true
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
    }

}

dependencies {
    implementation(platform(libs.com.google.firebase.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.photoview)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
    implementation(libs.billing.ktx)
    implementation(libs.vision.common)
    implementation(libs.play.services.mlkit.text.recognition.common)
    implementation(libs.play.services.mlkit.text.recognition)
    implementation(libs.pdfbox.android)
    implementation(libs.firebase.firestore.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.play.services.ads)
    implementation(libs.billing)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.installreferrer)
}