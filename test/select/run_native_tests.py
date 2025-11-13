#!/usr/bin/env python3
import subprocess, sys, pathlib

root = pathlib.Path(__file__).parent.parent.parent
run = lambda cmd: subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=root)

if run("mvn -q clean install -DskipTests -pl '!test'").returncode != 0:
    sys.exit("Build failed")

tests = [
    ("test_native_wildcard.ol", ["tree.a", "tree.c"]),
    ("test_native_simple_value.ol", ["data.x", "data.z"]),
    ("test_native_greater_than.ol", ["items.b", "items.c"]),
    ("test_native_string_match.ol", ["fruits.a", "fruits.c"]),
    ("test_native_not_equal.ol", ["vals.a", "vals.c"]),
    ("test_select_single.ol", ["myvar"]),
    ("test_select_single_no_match.ol", []),
    ("test_dollar_field.ol", ["tree.b", "tree.c"]),
    ("test_dollar_nested_field.ol", ["items.c"]),
    ("test_grandchildren.ol", ["tree.a.x", "tree.a.y", "tree.b.z"]),
    ("test_recursive_field.ol", ["tree.b.data.value", "tree.a.value"]),
    ("test_recursive_where.ol", ["tree.b"]),
    ("test_recursive_and.ol", ["tree.d.value", "tree.b.data.value"]),
    ("test_recursive_or.ol", ["tree.c.nested.value", "tree.a.value"]),
    ("test_recursive_not.ol", ["tree.a", "tree.c"]),
    ("test_recursive_where_and_field.ol", ["tree.b"]),
    ("test_recursive_complex.ol", ["tree.e.value", "tree.d.deep.value", "tree.b.data.value", "tree.a.value"]),
    ("test_where_array_wildcard_basic.ol", ["data.items[0]", "data.items[2]"]),
    ("test_where_array_wildcard_nested.ol", ["data.users[0]", "data.users[2]"]),
    ("test_where_array_wildcard_multiple.ol", ["data.users[1]"]),
    ("test_where_array_wildcard_empty.ol", ["data.items[1]"]),
    ("test_where_array_wildcard_negation.ol", ["data.items[0]", "data.items[1]", "data.items[2]"]),
    ("test_where_array_wildcard_inequality.ol", ["data.a", "data.b"]),
    ("test_where_array_wildcard_greater_than.ol", ["data.students[0]", "data.students[2]"]),
    ("test_where_array_wildcard_less_than.ol", ["data.products[0]", "data.products[2]"]),
    ("test_where_array_wildcard_boolean_and.ol", ["data.items[0]"]),
    ("test_where_array_wildcard_boolean_or.ol", ["data.tasks[0]", "data.tasks[1]", "data.tasks[3]"]),
    ("test_where_array_wildcard_string_comparison.ol", ["data.documents[0]", "data.documents[2]"]),
    ("test_where_array_wildcard_nonexistent_field.ol", ["data.items[0]"]),
    ("test_where_array_wildcard_mixed_types.ol", ["data.records[0]", "data.records[2]"]),
    ("test_where_array_wildcard_deep_nesting.ol", ["data.orgs[0]", "data.orgs[1]"]),
    ("test_where_array_wildcard_combined_path_and_where.ol", ["data.items[0]", "data.items[2]"]),
]

passed = 0
for test, expected in tests:
    out = run(f"JOLIE_HOME={root}/dist/jolie {root}/dist/launchers/unix/jolie test/select/{test}").stdout
    result = [line.strip() for line in out.strip().split('\n') if line.strip()]
    ok = result == expected
    print(f"{'✓' if ok else '✗'} {test}")
    if not ok: print(f"  Expected: {expected}\n  Got: {result}")
    passed += ok

print(f"\n{passed}/{len(tests)} passed")
sys.exit(0 if passed == len(tests) else 1)
