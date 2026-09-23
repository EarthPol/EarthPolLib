"""Exercise release selection against real temporary Git histories."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from release_version import parse_version, resolve_version


class ReleaseVersionTest(unittest.TestCase):
    def setUp(self):
        self.original_directory = Path.cwd()
        self.directory = tempfile.TemporaryDirectory()
        os.chdir(self.directory.name)
        self.addCleanup(self.directory.cleanup)
        self.addCleanup(os.chdir, self.original_directory)
        self.git("init", "--initial-branch=main")
        self.git("config", "user.name", "Release test")
        self.git("config", "user.email", "test@example.invalid")
        self.set_version("1.0.0")
        self.commit("Initial project")

    def git(self, *arguments):
        return subprocess.check_output(["git", *arguments], text=True, stderr=subprocess.PIPE).strip()

    def set_version(self, version):
        Path("pom.xml").write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0">'
            f"<version>{version}</version></project>", encoding="utf-8"
        )

    def commit(self, message):
        self.git("add", "pom.xml")
        self.git("commit", "--allow-empty", "-m", message)
        self.git("update-ref", "refs/remotes/origin/main", "HEAD")
        return self.git("rev-parse", "HEAD")

    def test_first_release_uses_pom_version(self):
        self.assertEqual(("1.0.0", False, self.git("rev-parse", "HEAD")), resolve_version())

    def test_next_main_commit_increments_patch_numerically(self):
        self.git("tag", "v1.0.9")
        self.commit("Next change")
        self.assertEqual(("1.0.10", False), resolve_version()[:2])

    def test_retry_keeps_version_after_later_releases(self):
        first = self.git("rev-parse", "HEAD")
        self.git("tag", "--annotate", "v1.0.0", "--message", "First")
        self.commit("Later change")
        self.git("tag", "v1.0.1")
        self.git("checkout", "--detach", first)
        self.assertEqual(("1.0.0", True, first), resolve_version())
        self.assertEqual(("1.0.0", True, first), resolve_version("v1.0.0"))

    def test_pom_can_start_new_minor_version(self):
        self.git("tag", "v1.0.9")
        self.set_version("1.1.0")
        self.commit("New minor")
        self.assertEqual(("1.1.0", False), resolve_version()[:2])

    def test_pom_can_start_new_major_version(self):
        self.git("tag", "v1.9.9")
        self.set_version("2.0.0")
        self.commit("New major")
        self.assertEqual(("2.0.0", False), resolve_version()[:2])

    def test_unrelated_tags_do_not_change_version(self):
        for tag in ("development", "v1.2.3-preview", "v01.0.0"):
            self.git("tag", tag)
        self.assertEqual(("1.0.0", False), resolve_version()[:2])

    def test_non_main_commit_cannot_be_released(self):
        self.git("switch", "-c", "feature")
        self.git("commit", "--allow-empty", "-m", "Unmerged")
        with self.assertRaisesRegex(ValueError, "merged into main"):
            resolve_version()

    def test_unreleased_older_commit_cannot_replace_newer_release(self):
        older = self.git("rev-parse", "HEAD")
        self.commit("Later change")
        self.git("tag", "v1.0.0")
        self.git("checkout", "--detach", older)
        with self.assertRaisesRegex(ValueError, "include the latest release"):
            resolve_version()

    def test_retry_rejects_missing_or_mismatched_tags(self):
        self.git("tag", "v1.0.0")
        self.commit("Next change")
        for tag in ("v1.0.0", "v1.0.1", "main", "--help"):
            with self.subTest(tag=tag), self.assertRaisesRegex(ValueError, "Retry tag"):
                resolve_version(tag)

    def test_ambiguous_tags_require_explicit_retry(self):
        self.git("tag", "v1.0.0")
        self.git("tag", "v1.0.1")
        with self.assertRaisesRegex(ValueError, "Multiple release tags"):
            resolve_version()
        self.assertEqual(("1.0.1", True), resolve_version("v1.0.1")[:2])

    def test_invalid_release_versions_are_rejected(self):
        for version in ("1.0.0-SNAPSHOT", "1.0", "01.0.0", "1.0.-1", "${revision}"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                parse_version(version)


if __name__ == "__main__":
    unittest.main()
