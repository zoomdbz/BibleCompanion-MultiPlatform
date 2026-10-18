"""Release configuration checks; these do not measure a release artifact's R8 scores."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ReleaseOptimizationContracts(unittest.TestCase):
    def test_agp9_and_gradle_use_a_compatible_verified_distribution(self):
        text = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        plugins = dict(re.findall(r'id\("([^"]+)"\) version "([^"]+)"', text))
        self.assertEqual("9.0.1", plugins["com.android.application"])
        self.assertEqual(
            plugins["com.android.application"],
            plugins["com.android.kotlin.multiplatform.library"],
        )
        self.assertEqual("2.3.20", plugins["org.jetbrains.kotlin.multiplatform"])
        self.assertEqual(
            plugins["org.jetbrains.kotlin.multiplatform"],
            plugins["org.jetbrains.kotlin.plugin.compose"],
        )
        self.assertEqual(
            plugins["org.jetbrains.kotlin.multiplatform"],
            plugins["org.jetbrains.kotlin.plugin.serialization"],
        )
        self.assertEqual("1.9.3", plugins["org.jetbrains.compose"])
        wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
        self.assertIn("gradle-9.1.0-bin.zip", wrapper)
        self.assertIn(
            "distributionSha256Sum=a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806",
            wrapper,
        )

    def test_optimized_resource_shrinking_cannot_be_disabled(self):
        properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
        self.assertIn("android.r8.optimizedResourceShrinking=true", properties)
        for setting in (
            "android.enableR8.fullMode=false",
            "android.r8.optimizedResourceShrinking=false",
            "android.newDsl=false",
            "android.builtInKotlin=false",
        ):
            self.assertNotIn(setting, properties)

    def test_android_application_preserves_its_sources_and_asset_pack(self):
        app = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertNotIn('id("org.jetbrains.kotlin.multiplatform")', app)
        self.assertIn('kotlin.srcDirs("src/androidMain/kotlin")', app)
        self.assertIn('res.srcDirs("src/androidMain/res")', app)
        self.assertIn('manifest.srcFile("src/androidMain/AndroidManifest.xml")', app)
        self.assertIn('assets.srcDirs("../shared/assets")', app)
        self.assertIn('assets.srcDirs("../embedding-assets/src/main/assets")', app)
        self.assertIn('android.assetPacks += listOf(":embedding-assets")', app)
        pack = (ROOT / "embedding-assets/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('packName.set("embedding_assets")', pack)
        self.assertIn('deliveryType.set("fast-follow")', pack)

    def test_shared_target_keeps_android_tests_resources_and_apple_framework(self):
        shared = (ROOT / "shared/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('id("com.android.kotlin.multiplatform.library")', shared)
        self.assertNotIn('id("com.android.library")', shared)
        self.assertNotIn("androidTarget", shared)
        self.assertRegex(shared, r"android\s*\{\s*namespace\s*=")
        self.assertIn("withHostTest", shared)
        self.assertRegex(shared, r"androidResources\s*\{\s*enable = true")
        self.assertIn("commonTest.dependencies", shared)
        self.assertIn('implementation(kotlin("test"))', shared)
        for target in ("iosX64()", "iosArm64()", "iosSimulatorArm64()"):
            self.assertIn(target, shared)
        self.assertIn('baseName = "shared"', shared)
        self.assertIn("isStatic = true", shared)
        self.assertIn('packageOfResClass = "com.dividesbyzer0.biblecompanion"', shared)

    def test_release_versions_match_on_android_and_apple(self):
        app = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        version_code = re.search(r"versionCode = (\d+)", app).group(1)
        version_name = re.search(r'versionName = "([\d.]+)"', app).group(1)
        apple = (ROOT / "iosApp/iosApp.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
        # The app and the embedded FeastWidgetExtension each carry Debug and
        # Release values; App Store validation expects every bundle to agree.
        build_numbers = re.findall(r"CURRENT_PROJECT_VERSION = (\d+);", apple)
        marketing_versions = re.findall(r"MARKETING_VERSION = ([\d.]+);", apple)
        self.assertEqual(4, len(build_numbers))
        self.assertEqual(4, len(marketing_versions))
        self.assertEqual({version_code}, set(build_numbers))
        self.assertEqual({version_name}, set(marketing_versions))
        # The toolchain upgrade must not drop support for previously supported devices.
        self.assertIn("minSdk = 24", app)
        # Project and app configurations stay at iOS 16.0; the embedded
        # FeastWidgetExtension needs iOS 17.0 for interactive widget buttons and
        # simply does not load on iOS 16 devices.
        self.assertEqual(
            ["16.0"] * 4 + ["17.0"] * 2,
            re.findall(r"IPHONEOS_DEPLOYMENT_TARGET = ([\d.]+);", apple),
        )

    def test_apple_cache_workaround_matches_the_current_kotlin_compiler(self):
        root = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        shared = (ROOT / "shared/build.gradle.kts").read_text(encoding="utf-8")
        kotlin_version = re.search(
            r'id\("org.jetbrains.kotlin.multiplatform"\) version "([\d.]+)"', root
        ).group(1)
        self.assertIn(
            "import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion", shared
        )
        self.assertIn(
            "import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi", shared
        )
        self.assertIn("@OptIn(KotlinNativeCacheApi::class)", shared)
        frameworks = shared.split("target.binaries.framework {", 1)[1].split("sourceSets {", 1)[0]
        self.assertIn("disableNativeCache(", frameworks)
        self.assertIn(
            "version = DisableCacheInKotlinVersion.`" + kotlin_version.replace(".", "_") + "`",
            frameworks,
        )
        for library in ("Reorderable", "extended icons"):
            self.assertIn(library, frameworks)
        properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
        self.assertNotIn("kotlin.native.cacheKind", properties)

    def test_navigation_matches_compose_and_rejects_unlinked_apple_archives(self):
        shared = (ROOT / "shared/build.gradle.kts").read_text(encoding="utf-8")
        navigation = re.findall(
            r'implementation\("org\.jetbrains\.androidx\.navigation:navigation-compose:([^\"]+)"\)',
            shared,
        )
        # 2.9.2 declares Compose 1.9.3, Lifecycle 2.9.6, and SavedState 1.3.6.
        # The previous alpha called removed APIs and crashed at the first NavHost.
        self.assertEqual(["2.9.2"], navigation)
        frameworks = shared.split("target.binaries.framework {", 1)[1].split("sourceSets {", 1)[0]
        # NativeBinary.freeCompilerArgs is List<String>, not a ListProperty.
        self.assertIn('freeCompilerArgs += "-Xpartial-linkage-loglevel=ERROR"', frameworks)
        self.assertNotIn("-Xpartial-linkage=disable", shared)
        self.assertNotIn("-Xpartial-linkage-loglevel=INFO", shared)

    def test_release_enables_code_and_resource_optimization_without_minifying_debug(self):
        text = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        types = text[text.index("buildTypes {"):text.index("buildFeatures {")]
        release, debug = types.split("debug {", 1)
        self.assertIn("isMinifyEnabled = true", release)
        self.assertIn("isShrinkResources = true", release)
        self.assertIn('getDefaultProguardFile("proguard-android-optimize.txt")', release)
        self.assertIn('"proguard-rules.pro"', release)
        self.assertIn("isMinifyEnabled = false", debug)

    def test_custom_keep_rules_preserve_reflection_entry_points_not_the_entire_app(self):
        path = ROOT / "app/proguard-rules.pro"
        rules = [line.strip() for line in path.read_text(encoding="utf-8").splitlines()
                 if line.strip() and not line.lstrip().startswith("#")]
        self.assertEqual([
            "-keep class ai.onnxruntime.** { *; }",
            "-keep class * extends androidx.room.RoomDatabase { public <init>(); }",
            "-keep class * extends androidx.work.InputMerger { public <init>(); }",
            "-keep class * implements androidx.glance.appwidget.action.ActionCallback { public <init>(); }",
        ], rules)
        for folder in (ROOT / "app", ROOT / "shared"):
            for path in folder.rglob("*.pro"):
                if "build" in path.relative_to(ROOT).parts:
                    continue
                text = path.read_text(encoding="utf-8")
                self.assertNotRegex(text, re.compile(r"^\s*-(?:dontobfuscate|dontoptimize|dontshrink)\b", re.M))


if __name__ == "__main__":
    unittest.main()
