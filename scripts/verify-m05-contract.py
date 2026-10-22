"""Check the M05 construction contract and positive/negative wire fixtures.

Requires jsonschema 4.x, as does verify-m03-contract.py. This check never
contacts a server or database and is not evidence that M05 is implemented.
"""
import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[1]
HTTP_METHODS = {"get", "post", "put", "patch", "delete", "options", "head"}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def resolve(document, pointer):
    require(pointer.startswith("#/"), f"Only local refs allowed: {pointer}")
    result = document
    for token in pointer[2:].split("/"):
        result = result[token.replace("~1", "/").replace("~0", "~")]
    return result


def check_refs(document, value):
    if isinstance(value, dict):
        if "$ref" in value:
            resolve(document, value["$ref"])
        for child in value.values():
            check_refs(document, child)
    elif isinstance(value, list):
        for child in value:
            check_refs(document, child)


def validate(spec, schema, body):
    wrapped = {**schema, "components": spec["components"]}
    return list(Draft202012Validator(wrapped, format_checker=FormatChecker()).iter_errors(body))


def main():
    spec = json.loads((ROOT / "source/common/openapi-m05.json").read_text(encoding="utf-8"))
    fixtures = json.loads((ROOT / "source/server/src/test/resources/contracts/m05-fixtures.json").read_text(encoding="utf-8"))
    require(spec["openapi"] == "3.1.0", "Use OpenAPI 3.1 JSON Schema semantics")
    require(fixtures["contractVersion"] == spec["info"]["version"], "Fixture version differs from contract")
    check_refs(spec, spec)
    for schema in spec["components"]["schemas"].values():
        Draft202012Validator.check_schema(schema)

    operation_ids = set()
    required_coverage = set()
    examples_checked = 0
    for path, path_item in spec["paths"].items():
        require(path.startswith("/api/me/"), f"Unexpected M05 route: {path}")
        for method, operation in path_item.items():
            if method not in HTTP_METHODS:
                continue
            operation_id = operation["operationId"]
            require(operation_id not in operation_ids, f"Duplicate operationId: {operation_id}")
            operation_ids.add(operation_id)
            require(operation.get("security") == [{"CookieAuth": []}], f"Missing session security: {operation_id}")
            require(operation.get("x-roles") == ["STUDENT"], f"Missing role boundary: {operation_id}")
            parameters = operation.get("parameters", [])
            require(len({(p["in"], p["name"]) for p in parameters}) == len(parameters), f"Duplicate parameter: {operation_id}")
            success = operation["responses"]["200"]
            require(success["headers"]["Cache-Control"]["schema"]["const"] == "no-store, private", f"Missing private cache policy: {operation_id}")
            for status, response in operation["responses"].items():
                media = response["content"]["application/json"]
                if "example" in media:
                    errors = validate(spec, media["schema"], media["example"])
                    require(not errors, f"Invalid response example for {operation_id}/{status}: {errors}")
                    examples_checked += 1
            required_coverage.add((path, method, "response"))
            if method == "post":
                csrf = [p for p in parameters if p["in"] == "header" and p["name"] == "X-CSRF-TOKEN"]
                require(len(csrf) == 1 and csrf[0]["required"], f"Missing CSRF boundary: {operation_id}")
                media = operation["requestBody"]["content"]["application/json"]
                require(operation["requestBody"]["required"], f"Optional command body: {operation_id}")
                require(resolve(spec, media["schema"]["$ref"])["additionalProperties"] is False, f"Command permits extra fields: {operation_id}")
                require(not validate(spec, media["schema"], media["example"]), f"Invalid command example: {operation_id}")
                examples_checked += 1
                required_coverage.add((path, method, "request"))
            else:
                require("requestBody" not in operation, f"Read endpoint takes body: {operation_id}")

    names = set()
    covered = set()
    checked = 0
    for group, expected_valid in (("valid", True), ("invalid", False)):
        for case in fixtures[group]:
            require(case["name"] not in names, f"Duplicate fixture name: {case['name']}")
            names.add(case["name"])
            resolve(spec, case["schema"])
            errors = validate(spec, {"$ref": case["schema"]}, case["body"])
            require((not errors) == expected_valid,
                    f"{case['name']}: expected valid={expected_valid}; errors=" + "; ".join(e.message for e in errors))
            if "path" in case:
                operation = spec["paths"][case["path"]][case["method"]]
                kind = case.get("kind", "response")
                declared = operation["requestBody"]["content"]["application/json"]["schema"] if kind == "request" else operation["responses"][str(case["status"])]["content"]["application/json"]["schema"]
                require(declared["$ref"] == case["schema"], f"Fixture targets wrong operation schema: {case['name']}")
                if expected_valid:
                    covered.add((case["path"], case["method"], kind))
            checked += 1
    require(required_coverage <= covered, f"Missing endpoint fixtures: {required_coverage - covered}")
    print(f"PASS: {len(operation_ids)} operations, {len(spec['components']['schemas'])} schemas, all local refs, {examples_checked} embedded examples")
    print(f"PASS: {checked} fixtures ({len(fixtures['valid'])} valid, {len(fixtures['invalid'])} invalid), endpoint request/response coverage complete")
    print("Scope: contract structure/wire validation only; no runtime, database, policy or concurrency assertions.")


if __name__ == "__main__":
    main()
