"""Validate M03 fixtures against local OpenAPI schemas (requires jsonschema 4.x).

Run with an isolated Python environment; this is a development check, not an app dependency.
"""
import json
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[1]


def main():
    spec = json.loads((ROOT / "source/common/openapi-m02-m03.json").read_text(encoding="utf-8"))
    fixtures = json.loads((ROOT / "source/server/src/test/resources/contracts/m03-fixtures.json").read_text(encoding="utf-8"))
    checked = 0
    for group, should_pass in (("valid", True), ("invalid", False)):
        for case in fixtures[group]:
            schema = {"$ref": case["schema"], "components": spec["components"]}
            validator = Draft202012Validator(schema, format_checker=FormatChecker())
            errors = list(validator.iter_errors(case["body"]))
            if (not errors) != should_pass:
                details = "; ".join(error.message for error in errors)
                raise AssertionError(f"{case['name']}: unexpected validation result {details}")
            checked += 1
    print(f"PASS: {checked} cases ({len(fixtures['valid'])} valid, {len(fixtures['invalid'])} invalid)")


if __name__ == "__main__":
    main()
