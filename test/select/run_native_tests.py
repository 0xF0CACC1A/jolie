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
