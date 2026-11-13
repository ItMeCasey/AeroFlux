"""Tests for plan_matrix.py. Run from the repo root: python -I .github/scripts/test_plan_matrix.py"""
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import plan_matrix as pm  # noqa: E402

FOLDERS = [e[1] for e in pm.ALL]


def folders(picked):
    return sorted(e[1] for e in picked)


def commit_exists(sha):
    r = subprocess.run(["git", "cat-file", "-e", sha + "^{commit}"], cwd=ROOT, capture_output=True)
    return r.returncode == 0


def real_diff(sha):
    r = subprocess.run(["git", "diff", "--name-only", sha + "^", sha], cwd=ROOT, capture_output=True, text=True)
    return [l for l in r.stdout.splitlines() if l.strip()]


class SyntheticFiles(unittest.TestCase):
    def test_docs_and_status_select_nothing(self):
        files = ["README.md", "PROJECT_STATUS.md", "TRANSLATING.md", "curseforge/CHANGELOG_2026.11.11.md",
                 "curseforge/images/aeroflux_header.png", "docs/COMPATIBILITY.md", "docs/frames/a.png",
                 "branding/x.png", "assets-src/aeroflux/textures/a.png", "concept/a.png", "LICENSE", ".gitignore",
                 "versions/build-results.txt", "versions/mc1.20.1_forge.build.log", ".github/pull_request_template.md"]
        self.assertEqual(pm.plan_for_files(files), ([], False))

    def test_workflow_and_ci_script_changes_select_nothing(self):
        files = [".github/workflows/build.yml", ".github/workflows/release.yml",
                 ".github/scripts/plan_matrix.py", ".github/scripts/publish_curseforge.py"]
        self.assertEqual(pm.plan_for_files(files), ([], False))

    def test_single_module(self):
        picked, core = pm.plan_for_files(["versions/mc1.20.1/neoforge/src/main/java/A.java", "PROJECT_STATUS.md"])
        self.assertEqual(folders(picked), ["versions/mc1.20.1/neoforge"])
        self.assertFalse(core)

    def test_module_libs_and_gradle_files_select_that_module(self):
        picked = pm.pick_for_files(["versions/mc26.3/neoforge/libs/smwti-api-26.3.jar", "versions/mc26.3/neoforge/build.gradle"])
        self.assertEqual(folders(picked), ["versions/mc26.3/neoforge"])

    def test_one_minecraft_version_both_loaders(self):
        picked = pm.pick_for_files(["versions/mc26.2/forge/gradle.properties", "versions/mc26.2/neoforge/gradle.properties"])
        self.assertEqual(folders(picked), ["versions/mc26.2/forge", "versions/mc26.2/neoforge"])

    def test_shared_core_sources_select_every_build_and_the_unit_tests(self):
        picked, core = pm.plan_for_files(["shared-core/src/main/java/com/itmecasey/aeroflux/core/Tier.java"])
        self.assertEqual(len(picked), 10)
        self.assertTrue(core)

    def test_shared_core_tests_and_gradle_select_only_the_unit_test_job(self):
        for f in ("shared-core/src/test/java/com/itmecasey/aeroflux/core/TierTest.java", "shared-core/build.gradle",
                  "shared-core/gradle/wrapper/gradle-wrapper.properties"):
            self.assertEqual(pm.plan_for_files([f]), ([], True), f)

    def test_translations_select_only_the_check(self):
        self.assertEqual(pm.plan_for_files(["lang/de_de.json", "assets-src/tools/lang_sync.py"]), ([], True))
        picked, core = pm.plan_for_files(["versions/mc26.2/forge/src/generated/resources/assets/aeroflux/lang/en_us.json"])
        self.assertEqual(folders(picked), ["versions/mc26.2/forge"])
        self.assertTrue(core)

    def test_run_logs_and_build_output_inside_a_module_select_nothing(self):
        files = ["versions/mc1.20.1/forge/run-data/logs/a.log.gz", "versions/mc1.20.1/forge/run-data/config/fml.toml",
                 "versions/mc1.20.1/forge/run/options.txt", "versions/mc1.20.1/forge/build/libs/x.jar",
                 "versions/mc1.20.1/forge/devlibs/bei.jar"]
        self.assertEqual(pm.plan_for_files(files), ([], False))

    def test_unknown_path_selects_everything(self):
        self.assertEqual(len(pm.pick_for_files(["tools/new_script.sh"])), 10)
        self.assertEqual(len(pm.pick_for_files(["README.md", "build.gradle"])), 10)
        self.assertEqual(len(pm.pick_for_files(["versions/new_tool.py"])), 10)

    def test_unknown_build_folder_selects_everything(self):
        self.assertEqual(len(pm.pick_for_files(["versions/mc27.0/forge/build.gradle"])), 10)

    def test_diff_failure_selects_everything(self):
        self.assertEqual(pm.plan_for_files(None), (list(pm.ALL), True))
        self.assertIsNone(pm.changed_files("0123456789abcdef0123456789abcdef01234567", "also-not-a-commit"))

    def test_matrix_fields_match_the_workflows(self):
        self.assertEqual(sorted(pm.matrix_for(pm.ALL[:1], False)["include"][0]), ["dir", "name"])
        self.assertEqual(sorted(pm.matrix_for(pm.ALL[:1], True)["include"][0]), ["dir", "java", "name"])


class ReleaseMode(unittest.TestCase):
    def make_tree(self, versions):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        for folder, version in versions.items():
            os.makedirs(os.path.join(tmp.name, folder))
            with open(os.path.join(tmp.name, folder, "gradle.properties"), "w", encoding="utf-8") as fh:
                fh.write(f"# comment\nmod_version={version}\n")
        return tmp.name

    def test_only_matching_versions(self):
        v = {f: "2026.11.10" for f in FOLDERS}
        v["versions/mc26.3/forge"] = "2026.11.11"
        v["versions/mc26.3/neoforge"] = "2026.11.11"
        root = self.make_tree(v)
        self.assertEqual(folders(pm.pick_for_release("2026.11.11", root)), ["versions/mc26.3/forge", "versions/mc26.3/neoforge"])
        self.assertEqual(len(pm.pick_for_release("2026.11.10", root)), 8)
        self.assertEqual(pm.pick_for_release("2026.11.12", root), [])
        self.assertEqual(pm.newest_version(root), "2026.11.11")

    def test_all_modules_carry_the_tag(self):
        root = self.make_tree({f: "2026.12.1" for f in FOLDERS})
        self.assertEqual(len(pm.pick_for_release("2026.12.1", root)), 10)

    def test_newest_version_sorts_numerically(self):
        v = {f: "2026.11.9" for f in FOLDERS}
        v["versions/mc26.3/forge"] = "2026.11.10"
        self.assertEqual(pm.newest_version(self.make_tree(v)), "2026.11.10")


class Cli(unittest.TestCase):
    def run_cli(self, *args, ok=True):
        r = subprocess.run([sys.executable, "-I", os.path.join(HERE, "plan_matrix.py"), *args], cwd=ROOT, capture_output=True, text=True)
        if ok:
            self.assertEqual(r.returncode, 0, r.stderr)
        return r

    def test_build_with_files(self):
        out = self.run_cli("build", "--files", "README.md", ".github/workflows/build.yml").stdout
        self.assertIn("any=false", out)
        self.assertIn("core=false", out)
        out = self.run_cli("build", "--files", "versions/mc26.2/forge/src/x.java").stdout
        self.assertIn("any=true", out)
        self.assertIn("forge-26.2", out)

    def test_release_with_a_tag_nobody_has(self):
        self.assertIn("any=false", self.run_cli("release", "--tag", "v1999.1.1").stdout)
        self.assertEqual(self.run_cli("release", "--tag", "v1999.1.1", "--require-any", ok=False).returncode, 1)

    def test_release_with_the_newest_version(self):
        r = self.run_cli("release", "--require-any")
        self.assertIn("any=true", r.stdout)
        self.assertIn("version=" + pm.newest_version(ROOT), r.stdout)

    def test_unusable_range_selects_everything(self):
        out = self.run_cli("build", "--before", "0123456789abcdef0123456789abcdef01234567", "--sha", "HEAD").stdout
        self.assertIn("any=true", out)
        self.assertEqual(out.count('"name"'), 10)


class RealCommits(unittest.TestCase):
    def check(self, sha, expected, core=None):
        if not commit_exists(sha):
            self.skipTest(f"commit {sha} is not in this clone")
        picked, got_core = pm.plan_for_files(real_diff(sha))
        self.assertEqual(folders(picked), sorted(expected))
        if core is not None:
            self.assertEqual(got_core, core)

    def test_status_only(self):
        self.check("c6b0636", [], False)
        self.check("0b615a2", [], False)

    def test_docs_and_frames_only(self):
        self.check("7d06825", [], False)

    def test_workflow_and_release_script_only(self):
        self.check("be2e108", [], False)
        self.check("b0b1248", [], False)

    def test_tracked_run_logs_removed(self):
        self.check("73551bb", [], False)

    def test_one_minecraft_version(self):
        self.check("2925455", ["versions/mc26.3/forge", "versions/mc26.3/neoforge"], False)

    def test_six_older_builds(self):
        self.check("2f80cc9", [e for e in FOLDERS if "mc26.2" not in e and "mc26.3" not in e], False)

    def test_shared_core_change_selects_everything(self):
        self.check("cd4c2e9", FOLDERS, True)


class CurrentTree(unittest.TestCase):
    def test_every_module_has_a_version(self):
        for e in pm.ALL:
            self.assertTrue(pm.module_version(os.path.join(ROOT, e[1])), e[1])

    def test_matrix_matches_the_repo_folders(self):
        on_disk = sorted(f"versions/{a}/{b}" for a in os.listdir(os.path.join(ROOT, "versions"))
                         for b in ("forge", "neoforge") if os.path.isfile(os.path.join(ROOT, "versions", a, b, "gradle.properties")))
        self.assertEqual(sorted(FOLDERS), on_disk)

    def test_every_build_compiles_shared_core_the_same_way(self):
        for e in pm.ALL:
            with open(os.path.join(ROOT, e[1], "build.gradle"), encoding="utf-8") as fh:
                self.assertIn("../../../shared-core/src/main/java", fh.read(), e[1])


if __name__ == "__main__":
    unittest.main()
