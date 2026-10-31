#!/usr/bin/env python3
"""Fail CI when Gradle produced no executed tests or unsuccessful results."""

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", type=Path)
    args = parser.parse_args()
    reports = sorted(args.reports.glob("TEST-*.xml"))
    if not reports:
        parser.exit(1, "No JUnit XML reports found\n")

    totals = dict.fromkeys(("tests", "skipped", "failures", "errors"), 0)
    try:
        for report in reports:
            suite = ET.parse(report).getroot()
            if suite.tag != "testsuite":
                raise ValueError(f"Unexpected report root: {report.name}")
            counts = {key: int(suite.attrib.get(key, "0")) for key in totals}
            if any(value < 0 for value in counts.values()):
                raise ValueError(f"Negative count in {report.name}")
            if counts["skipped"] > counts["tests"]:
                raise ValueError(f"Invalid skipped count in {report.name}")
            for key, value in counts.items():
                totals[key] += value
    except (ET.ParseError, OSError, ValueError) as error:
        parser.exit(1, f"Invalid JUnit report: {error}\n")

    executed = totals["tests"] - totals["skipped"]
    print(f"Executed: {executed}; skipped: {totals['skipped']}; "
          f"failures: {totals['failures']}; errors: {totals['errors']}")
    return int(executed == 0 or totals["failures"] > 0 or totals["errors"] > 0)


if __name__ == "__main__":
    sys.exit(main())
