"""Release configuration checks; these do not measure a release artifact's R8 scores."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ReleaseOptimizationContracts(unittest.TestCase):
    def test_release_enables_code_and_resource_optimization_without_minifying_debug(self):
        text = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        types = text[text.index("buildTypes {"):text.index("buildFeatures {")]
        release, debug = types.split("debug {", 1)
        self.assertIn("isMinifyEnabled = true", release)
        self.assertIn("isShrinkResources = true", release)
        self.assertIn('getDefaultProguardFile("proguard-android-optimize.txt")', release)
        self.assertIn('"proguard-rules.pro"', release)
        self.assertIn("isMinifyEnabled = false", debug)

    def test_custom_keep_rules_preserve_the_native_engine_not_the_entire_app(self):
        path = ROOT / "app/proguard-rules.pro"
        rules = [line.strip() for line in path.read_text(encoding="utf-8").splitlines()
                 if line.strip() and not line.lstrip().startswith("#")]
        self.assertEqual(["-keep class ai.onnxruntime.** { *; }"], rules)
        for folder in (ROOT / "app", ROOT / "shared"):
            for path in folder.rglob("*.pro"):
                if "build" in path.relative_to(ROOT).parts:
                    continue
                text = path.read_text(encoding="utf-8")
                self.assertNotRegex(text, re.compile(r"^\s*-(?:dontobfuscate|dontoptimize|dontshrink)\b", re.M))


if __name__ == "__main__":
    unittest.main()
