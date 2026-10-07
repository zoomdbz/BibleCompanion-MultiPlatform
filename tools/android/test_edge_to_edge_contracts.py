"""Read-only edge-to-edge source checks; native layout testing remains required."""

from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "app/src/androidMain"
KOTLIN = ANDROID / "kotlin/com/dividesbyzer0/biblecompanion"
COMMON = ROOT / "shared/src/commonMain/kotlin/com/dividesbyzer0/biblecompanion"


class EdgeToEdgeContracts(unittest.TestCase):
    def test_every_activity_enables_edge_to_edge_before_displaying_content(self):
        manifest = ET.parse(ANDROID / "AndroidManifest.xml")
        name = "{http://schemas.android.com/apk/res/android}name"
        files = {
            ".MainActivity": "MainActivity.kt",
            ".CopyDailyVerseActivity": "DailyVerseNotificationScheduler.kt",
        }
        for activity in manifest.findall("application/activity"):
            activity_name = activity.get(name)
            with self.subTest(activity=activity_name):
                self.assertIn(activity_name, files)
                text = (KOTLIN / files[activity_name]).read_text(encoding="utf-8")
                class_name = activity_name.removeprefix(".")
                activity_text = text[text.index("class " + class_name):]
                self.assertIn("enableEdgeToEdge()", activity_text)
                self.assertLess(activity_text.index("super.onCreate(savedInstanceState)"),
                                activity_text.index("enableEdgeToEdge()"))
                content = "setContent {" if class_name == "MainActivity" else "setContentView(message)"
                self.assertLess(activity_text.index("enableEdgeToEdge()"), activity_text.index(content))

    def test_theme_does_not_set_deprecated_window_colors_or_opt_out(self):
        for path in (ANDROID / "res").rglob("*.xml"):
            with self.subTest(path=path.relative_to(ROOT)):
                xml = ET.parse(path)
                for item in xml.findall(".//item"):
                    self.assertNotIn(item.get("name"), {
                        "android:statusBarColor", "android:navigationBarColor",
                        "android:navigationBarDividerColor", "android:enforceStatusBarContrast",
                        "android:windowOptOutEdgeToEdgeEnforcement",
                    })
        for path in KOTLIN.rglob("*.kt"):
            with self.subTest(path=path.relative_to(ROOT)):
                text = path.read_text(encoding="utf-8")
                self.assertNotRegex(text, r"\b(?:setStatusBarColor|setNavigationBarColor|setDecorFitsSystemWindows)\s*\(")
                self.assertNotRegex(text, r"\b(?:statusBarColor|navigationBarColor|systemUiVisibility)\s*=")

    def test_root_draws_the_theme_behind_the_consumed_safe_area(self):
        text = (COMMON / "AppRoot.kt").read_text(encoding="utf-8")
        self.assertIn("val systemBarBackground: Modifier = if (isApplePlatform) Modifier", text)
        self.assertIn("else Modifier.background(MaterialTheme.colorScheme.background)", text)
        self.assertRegex(text, re.compile(
            r"BoxWithConstraints\(\s*Modifier\s*\.fillMaxSize\(\)\s*"
            r"\.then\(systemBarBackground\)\s*"
            r"\.windowInsetsPadding\(WindowInsets.safeDrawing\)", re.MULTILINE))
        self.assertIn("contentWindowInsets = WindowInsets(0, 0, 0, 0)", text)
        navigation = (COMMON / "AppMainNavigation.kt").read_text(encoding="utf-8")
        self.assertIn("NavigationRail(windowInsets = noInsets)", navigation)
        self.assertIn("NavigationBar(windowInsets = noInsets)", navigation)

    def test_platform_boolean_properties_are_not_called_as_functions(self):
        platform = (COMMON / "platform/Platform.kt").read_text(encoding="utf-8")
        properties = re.findall(r"\bexpect\s+val\s+(\w+)\s*:\s*Boolean\b", platform)
        self.assertIn("isApplePlatform", properties)
        for path in COMMON.rglob("*.kt"):
            text = path.read_text(encoding="utf-8")
            with self.subTest(path=path.relative_to(ROOT)):
                for name in properties:
                    self.assertNotRegex(text, rf"\b{re.escape(name)}\s*\(")

    def test_system_bar_icon_appearance_tracks_the_app_theme(self):
        text = (KOTLIN / "MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn("val prefs by repo.flow.collectAsState(init)", text)
        self.assertIn('"dark" -> true', text)
        self.assertIn('"light" -> false', text)
        self.assertIn("else -> isSystemInDarkTheme()", text)
        self.assertIn("isAppearanceLightStatusBars = !dark", text)
        self.assertIn("isAppearanceLightNavigationBars = !dark", text)
        self.assertIn("LaunchedEffect(dark)", text)
        self.assertRegex(text, r"if \(Build.VERSION.SDK_INT < 35\)\s*\{\s*enableEdgeToEdge\(")
        self.assertIn("SystemBarStyle.auto(Color.TRANSPARENT, Color.BLACK) { dark }", text)
        # Repeated enableEdgeToEdge calls can create duplicate protection views.
        self.assertEqual(1, text.count("enableEdgeToEdge()"))

    def test_notification_copy_screen_respects_bars_and_cutouts(self):
        text = (KOTLIN / "DailyVerseNotificationScheduler.kt").read_text(encoding="utf-8")
        copy = text[text.index("class CopyDailyVerseActivity") :]
        self.assertIn("CopyDailyVerseActivity : ComponentActivity()", copy)
        self.assertIn("ViewCompat.setOnApplyWindowInsetsListener(message)", copy)
        self.assertIn("WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()", copy)
        self.assertIn("view.setPadding(32 + safe.left, 48 + safe.top, 32 + safe.right, 48 + safe.bottom)", copy)
        self.assertIn("ViewCompat.requestApplyInsets(message)", copy)
        self.assertIn("setPrimaryClip(", copy)

    def test_android_dependencies_include_api_35_protection_and_stable_material(self):
        text = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('implementation("androidx.activity:activity-compose:1.12.4")', text)
        self.assertIn('implementation("com.google.android.material:material:1.13.0")', text)
        self.assertIn("compileSdk = 36", text)
        self.assertIn("targetSdk = 36", text)
        self.assertIn("minSdk = 24", text)


if __name__ == "__main__":
    unittest.main()
