import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutlibraries.android)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun String.asBuildConfigString(): String =
    "\"${
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }\""

val fallbackDataServerUrl = "archive-tune-admin-remote.vercel.app"
val dataServerUrl =
    rootProject
        .file("DataServer.txt")
        .takeIf { it.isFile }
        ?.readText()
        ?.trim()
        ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        ?: fallbackDataServerUrl
val apiBearerToken = System.getenv("API_BEARER_TOKEN")?.trim()
    ?: localProperties.getProperty("API_BEARER_TOKEN")?.trim()
    ?: ""

val discordApplicationId =
    (
        localProperties.getProperty("DISCORD_APPLICATION_ID")
            ?: System.getenv("DISCORD_APPLICATION_ID")
            ?: "1165706613961789445"
        ).trim()
val discordApplicationIdLong = discordApplicationId.toLongOrNull() ?: 1165706613961789445L
val discordRedirectScheme = "discord-$discordApplicationId"
val releaseKeystoreFile = file("keystore/release.keystore")
val releaseStorePassword =
    System.getenv("STORE_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val hasReleaseSigningConfig =
    releaseKeystoreFile.isFile &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
android {
    namespace = "moe.rukamori.archivetune"
    compileSdk = 37
    ndkVersion = "30.0.16248370"
    compileSdkMinor = 2

    defaultConfig {
    applicationId = "moe.rukamori.archivetune"
        minSdk = 26
        targetSdk = 37
        versionCode = 147
        versionName = "16.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        val lastfmApiKey =
            localProperties.getProperty("LASTFM_API_KEY")
                ?: System.getenv("LASTFM_API_KEY")
                ?: ""
        val lastfmSecret =
            localProperties.getProperty("LASTFM_SECRET")
                ?: System.getenv("LASTFM_SECRET")
                ?: ""
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmApiKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastfmSecret\"")

        val togetherBearerToken =
            localProperties.getProperty("TOGETHER_BEARER_TOKEN")
                ?: System.getenv("TOGETHER_BEARER_TOKEN")
                ?: ""
        buildConfigField("String", "TOGETHER_BEARER_TOKEN", "\"$togetherBearerToken\"")

        val sourceProviderUrl =
            (
                localProperties.getProperty("SOURCE_PROVIDER_URL")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("SOURCE_PROVIDER_URL")?.takeIf { it.isNotBlank() }
                    ?: "https://archivepool.vercel.app"
                ).trim().trimEnd('/')
        buildConfigField("String", "SOURCE_PROVIDER_URL", "\"$sourceProviderUrl\"")

        val sourceProviderKey =
            (
                localProperties.getProperty("SOURCE_PROVIDER_KEY")
                    ?: System.getenv("SOURCE_PROVIDER_KEY")
                    ?: ""
                ).trim()
        buildConfigField("String", "SOURCE_PROVIDER_KEY", "\"$sourceProviderKey\"")

        // [FIX-ZCODE] Porting completion (donor 18.0): Telegram integration credentials.
        // Defaults mirror the donor's public fallbacks; override via local.properties.
        val telegramApiId =
            (
                localProperties.getProperty("TELEGRAM_API_ID")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("TELEGRAM_API_ID")?.takeIf { it.isNotBlank() }
                    ?: "2040"
            ).trim()
        val telegramApiHash =
            (
                localProperties.getProperty("TELEGRAM_API_HASH")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("TELEGRAM_API_HASH")?.takeIf { it.isNotBlank() }
                    ?: "b18441a1ff607e10a989891a5462e627"
            ).trim()
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")

        // [FIX-ZCODE] tdlib native: slim mode (no bundled .so; downloaded at runtime)
        val slimTdlib = (project.findProperty("slimTdlib") as String?)?.toBoolean() ?: true
        buildConfigField("boolean", "TDLIB_BUNDLED", "${!slimTdlib}")
        buildConfigField(
            "String",
            "TDLIB_NATIVE_BASE_URL",
            "\"${project.findProperty("tdlibNativeBaseUrl") as String?
                ?: "https://github.com/4nx3B/ArchiveTune/releases/download/tdlight-2b51b33"}\"",
        )

        val poolClientKey =
            (
                localProperties.getProperty("POOL_CLIENT_KEY")
                    ?: System.getenv("POOL_CLIENT_KEY")
                    ?: ""
                ).trim()
        buildConfigField("String", "POOL_CLIENT_KEY", "\"$poolClientKey\"")

        buildConfigField("String", "DATA_SERVER_URL", dataServerUrl.asBuildConfigString())
        buildConfigField("String", "API_BEARER_TOKEN", apiBearerToken.asBuildConfigString())
        buildConfigField("boolean", "GATEKEEPER_ENABLED", "false")
        buildConfigField("boolean", "LEAK_CANARY_TOGGLE_AVAILABLE", "false")

        val nightlyBuildHash =
            (
                localProperties.getProperty("NIGHTLY_BUILD_HASH")
                    ?: System.getenv("NIGHTLY_BUILD_HASH")
                    ?: ""
                ).trim()
        buildConfigField("String", "NIGHTLY_BUILD_HASH", "\"$nightlyBuildHash\"")

        val nightlyVersionName =
            providers.gradleProperty("nightlyVersionName").orNull?.trim().orEmpty()
        if (nightlyVersionName.isNotEmpty()) {
            versionName = nightlyVersionName
        }
        buildConfigField("String", "DISTRIBUTION", "\"gms\"")
        buildConfigField("boolean", "UPDATER_AVAILABLE", "true")

        val githubOwner =
            System.getenv("GITHUB_OWNER")?.trim()
                ?: localProperties.getProperty("GITHUB_OWNER")?.trim()
                ?: "rukamori"
        val githubRepo =
            System.getenv("GITHUB_REPO")?.trim()
                ?: localProperties.getProperty("GITHUB_REPO")?.trim()
                ?: "ArchiveTune"
        buildConfigField("String", "GITHUB_OWNER", githubOwner.asBuildConfigString())
        buildConfigField("String", "GITHUB_REPO", githubRepo.asBuildConfigString())
        buildConfigField("boolean", "IS_NIGHTLY_BUILD", "false")

        val releaseGithubOwner =
            System.getenv("RELEASE_GITHUB_OWNER")?.trim()
                ?: localProperties.getProperty("RELEASE_GITHUB_OWNER")?.trim()
                ?: githubOwner
        val releaseGithubRepo =
            System.getenv("RELEASE_GITHUB_REPO")?.trim()
                ?: localProperties.getProperty("RELEASE_GITHUB_REPO")?.trim()
                ?: githubRepo
        buildConfigField("String", "RELEASE_GITHUB_OWNER", releaseGithubOwner.asBuildConfigString())
        buildConfigField("String", "RELEASE_GITHUB_REPO", releaseGithubRepo.asBuildConfigString())
    }

    flavorDimensions += listOf("distribution", "device", "abi")
    productFlavors {
        create("gms") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("String", "DISTRIBUTION", "\"gms\"")
            buildConfigField("boolean", "UPDATER_AVAILABLE", "true")
            buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordApplicationId\"")
            buildConfigField("long", "DISCORD_APPLICATION_ID_LONG", "${discordApplicationIdLong}L")
            buildConfigField("String", "DISCORD_REDIRECT_SCHEME", "\"$discordRedirectScheme\"")
            manifestPlaceholders["discordRedirectScheme"] = discordRedirectScheme
        }
        create("foss") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"foss\"")
            buildConfigField("boolean", "UPDATER_AVAILABLE", "true")
            buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordApplicationId\"")
            buildConfigField("long", "DISCORD_APPLICATION_ID_LONG", "${discordApplicationIdLong}L")
            buildConfigField("String", "DISCORD_REDIRECT_SCHEME", "\"$discordRedirectScheme\"")
            manifestPlaceholders["discordRedirectScheme"] = discordRedirectScheme
        }
        create("mobile") {
            dimension = "device"
            buildConfigField("String", "DEVICE", "\"mobile\"")
        }
        create("tv") {
            dimension = "device"
            buildConfigField("String", "DEVICE", "\"tv\"")
        }
        create("automotive") {
            dimension = "device"
            minSdk = 28
            buildConfigField("String", "DEVICE", "\"automotive\"")
        }
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
            buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        }
        create("arm64") {
            dimension = "abi"
            ndk { abiFilters += "arm64-v8a" }
            buildConfigField("String", "ARCHITECTURE", "\"arm64\"")
        }
        create("armeabi") {
            dimension = "abi"
            ndk { abiFilters += "armeabi-v7a" }
            buildConfigField("String", "ARCHITECTURE", "\"armeabi\"")
        }
        create("x86") {
            dimension = "abi"
            ndk { abiFilters += "x86" }
            buildConfigField("String", "ARCHITECTURE", "\"x86\"")
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += "x86_64" }
            buildConfigField("String", "ARCHITECTURE", "\"x86_64\"")
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("boolean", "GATEKEEPER_ENABLED", "false")
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        create("nightly") {
            applicationIdSuffix = ".nightly"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "LEAK_CANARY_TOGGLE_AVAILABLE", "true")
            buildConfigField("boolean", "IS_NIGHTLY_BUILD", "true")
            matchingFallbacks += listOf("release")

            val nightlyReleaseOwner =
                System.getenv("NIGHTLY_RELEASE_GITHUB_OWNER")?.trim()
                    ?: localProperties.getProperty("NIGHTLY_RELEASE_GITHUB_OWNER")?.trim()
                    ?: "rukamori"
            val nightlyReleaseRepo =
                System.getenv("NIGHTLY_RELEASE_GITHUB_REPO")?.trim()
                    ?: localProperties.getProperty("NIGHTLY_RELEASE_GITHUB_REPO")?.trim()
                    ?: "canary"
            buildConfigField("String", "RELEASE_GITHUB_OWNER", nightlyReleaseOwner.asBuildConfigString())
            buildConfigField("String", "RELEASE_GITHUB_REPO", nightlyReleaseRepo.asBuildConfigString())
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = false
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }

}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)
    implementation(libs.work.runtime)
    implementation("androidx.browser:browser:1.10.0")

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    compileOnly("androidx.compose.ui:ui-tooling-preview:${libs.versions.compose.get()}")
    debugImplementation("androidx.compose.ui:ui-tooling-preview:${libs.versions.compose.get()}")
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.leakcanary.android)
    add("nightlyImplementation", libs.leakcanary.android)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.material3)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.liquid.glass)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.palette)
    implementation(libs.androidsvg)
    implementation(libs.aboutlibraries.core)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.html)
    implementation(libs.markwon.image)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.simple.ext)

    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    // [FIX-ZCODE] Wave 9 / perf: ImmutableList for track lists (Compose smart skipping)
    implementation(libs.kotlinx.collections.immutable)

    // [FIX-ZCODE] Докрутка портирования 18.0: Lottie-анимации (ArchiveTuneLottie, лайк/загрузка/empty-state)
    implementation("com.airbnb.android:lottie-compose:6.6.6")

    // [FIX-ZCODE] Докрутка портирования 18.0: ktor content negotiation (Musixmatch/лирика)
    // и PRDownloader (donor PRDownloaderDataSource / выгрузка арта)
    implementation("io.ktor:ktor-client-content-negotiation:${libs.versions.ktor.get()}")
    implementation("com.github.amitshekhariitbhu:PRDownloader:1.0.2")
    // [FIX-ZCODE] Musixmatch lyrics provider module (donor 18.0)
    implementation(project(":musixmatch"))
    // [FIX-ZCODE] direct extractor access for like-count lookup (TikTok rail); previously core-only implementation dep
    implementation(libs.newpipe.extractor)

    implementation(libs.shimmer)

    // Glance Widget support
    implementation("androidx.glance:glance:1.1.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    implementation(libs.media3)
    implementation("androidx.media3:media3-exoplayer-hls:${libs.versions.media3.get()}")
    implementation(libs.media3.session)
    implementation(libs.car.app)
    implementation(libs.media3.okhttp)
    implementation("androidx.media3:media3-ui:${libs.versions.media3.get()}")
    implementation("androidx.media3:media3-ui-compose:${libs.versions.media3.get()}")
    add("gmsImplementation", libs.media3.cast)
    add("gmsImplementation", libs.mediarouter)
    add("gmsImplementation", "com.google.android.gms:play-services-auth:22.0.0")
    implementation(libs.squigglyslider)


    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    implementation(libs.re2j)
    annotationProcessor(libs.kotlin.metadata.jvm)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)

    implementation(project(":core"))
    implementation(project(":lyrics:kugou"))
    implementation(project(":lyrics:lrclib"))
    implementation(project(":lyrics:simpmusic"))
    implementation(project(":lyrics:paxsenix"))
    implementation(project(":lyrics:betterlyrics"))
    implementation(project(":lyrics:unison"))
    implementation(project(":lyrics:youlyplus"))
    implementation(project(":lastfm"))
    implementation(project(":canvas"))
    implementation(project(":shazamkit"))
    implementation(project(":spotifycore"))
    implementation(project(":morideobfuscator"))
    implementation("com.materialkolor:material-kolor:5.0.0-alpha07")

    // Spotify Librespot & Native Playback
    // [FIX-ZCODE] Wave 9: the fork overrides six librespot classes with same-package sources
    // (core/Session, core/ApResolver, core/TokenProvider, crypto/CipherPair, dealer/ApiClient,
    // dealer/DealerClient), so the stock librespot jar can't be used — R8 (fullMode) fails
    // on duplicate classes like Session$2. We ship a pre-stripped jar (app/libs/, rebuilt
    // via scripts when librespot is bumped) plus its transitive dependencies explicitly.
    implementation(files("libs/librespot-lib-1.6.5-overridden-stripped.jar"))
    implementation("xyz.gianlu.librespot:librespot-decoder-api:1.6.5")
    implementation("org.jcraft:jorbis:0.0.17")
    implementation("com.badlogicgames.jlayer:jlayer:1.0.2-gdx")
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("xyz.gianlu.zeroconf:zeroconf:1.3.2")
    implementation("commons-net:commons-net:3.11.1")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation(libs.conscrypt.android)
    implementation(libs.androidx.security.crypto)

    implementation(libs.webkit)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    implementation(libs.translator)
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0-rc01")
    implementation(libs.accompanist.lyrics.ui)
    implementation(libs.accompanist.lyrics.core)

    // [FIX-ZCODE] donor parity: audio tag writing for ExportDownloadedSongsScreen
    implementation("com.github.RouHim:jaudiotagger:1.4.31")
}

androidComponents {
    onVariants(selector().all()) { variant ->
        if (variant.productFlavors.any { (_, flavor) -> flavor == "automotive" }) return@onVariants
        val capitalizedVariantName =
            variant.name.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase() else character.toString()
            }
        val generateIconPack =
            tasks.register<GenerateIconPackTask>("generate${capitalizedVariantName}IconPack") {
                metadataFile.set(rootProject.layout.projectDirectory.file("IconPack/metadata.json"))
                svgDirectory.set(rootProject.layout.projectDirectory.dir("IconPack/svg"))
                applicationId.set(variant.applicationId)
                targetActivityClassName.set("moe.rukamori.archivetune.MainActivity")
                resourceOutputDirectory.set(
                    layout.buildDirectory.dir("generated/iconPack/${variant.name}/res"),
                )
                assetOutputDirectory.set(
                    layout.buildDirectory.dir("generated/iconPack/${variant.name}/assets"),
                )
                manifestOutputFile.set(
                    layout.buildDirectory.file(
                        "generated/iconPack/${variant.name}/AndroidManifest.xml",
                    ),
                )
            }

        variant.sources.res?.addGeneratedSourceDirectory(
            generateIconPack,
            GenerateIconPackTask::resourceOutputDirectory,
        )
        variant.sources.assets?.addGeneratedSourceDirectory(
            generateIconPack,
            GenerateIconPackTask::assetOutputDirectory,
        )
        variant.sources.manifests.addGeneratedManifestFile(
            generateIconPack,
            GenerateIconPackTask::manifestOutputFile,
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn"
        )
        // Suppress warnings
        suppressWarnings.set(true)
    }
}

configurations.configureEach {
    exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    // [FIX-ZCODE] W10: jaudiotagger pulls Maven org.json:json whose classes clash with the
    // Android bootclasspath org.json under R8 fullMode -> NoSuchMethodError
    // (JSONObject.put) / NoSuchFieldError (JSONArray.myArrayList) in release builds only.
    // Tag writing does not use the JSON helpers, so the Maven artifact is safe to drop.
    exclude(group = "org.json", module = "json")
    resolutionStrategy.eachDependency {
        when (requested.group) {
            "androidx.compose.runtime",
            "androidx.compose.foundation",
            "androidx.compose.ui",
            "androidx.compose.animation" -> {
                useVersion(libs.versions.compose.get())
                because("Keep Compose aligned with the RectList alignment-placement fix")
            }
        }
    }
    resolutionStrategy.force(
        "org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlinMetadata.get()}",
    )
}
