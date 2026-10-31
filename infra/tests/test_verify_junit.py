from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "verify_junit.py"


class VerifyJunitTests(unittest.TestCase):
    def run_report(self, xml=None):
        with tempfile.TemporaryDirectory() as directory:
            if xml is not None:
                (Path(directory) / "TEST-example.xml").write_text(xml, encoding="utf-8")
            return subprocess.run(
                [sys.executable, str(SCRIPT), directory],
                text=True, capture_output=True, check=False,
            )

    def test_accepts_executed_tests(self):
        result = self.run_report('<testsuite tests="2" skipped="1" failures="0" errors="0"/>')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Executed: 1", result.stdout)

    def test_rejects_missing_reports(self):
        self.assertNotEqual(self.run_report().returncode, 0)

    def test_rejects_zero_tests(self):
        self.assertNotEqual(self.run_report('<testsuite tests="0"/>').returncode, 0)

    def test_rejects_all_skipped(self):
        self.assertNotEqual(self.run_report('<testsuite tests="1" skipped="1"/>').returncode, 0)

    def test_rejects_failure(self):
        self.assertNotEqual(self.run_report('<testsuite tests="1" failures="1"/>').returncode, 0)

    def test_rejects_error(self):
        self.assertNotEqual(self.run_report('<testsuite tests="1" errors="1"/>').returncode, 0)

    def test_rejects_malformed_report(self):
        self.assertNotEqual(self.run_report('<testsuite tests="many"/>').returncode, 0)
        self.assertNotEqual(self.run_report('<testsuite').returncode, 0)

    def test_rejects_invalid_counts(self):
        self.assertNotEqual(self.run_report('<testsuite tests="1" skipped="2"/>').returncode, 0)
        self.assertNotEqual(self.run_report('<testsuite tests="-1"/>').returncode, 0)


if __name__ == "__main__":
    unittest.main()
