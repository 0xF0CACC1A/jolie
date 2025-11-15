#!/usr/bin/env python3
import subprocess, sys, pathlib, concurrent.futures

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
    # Array wildcard in PATHS path
    ("test_array_wildcard_base.ol", ["data[1]"]),
    ("test_array_wildcard_field.ol", ["tree.items[1]", "tree.items[2]"]),
    ("test_array_wildcard_nested.ol", ["data.users.list[1]", "data.users.list[2]"]),
    ("test_array_wildcard_objects.ol", ["users[0]", "users[1]"]),
    ("test_array_wildcard_string.ol", ["data[1]"]),
    ("test_array_wildcard_boolean.ol", ["data[1]", "data[2]"]),
    ("test_array_wildcard_nonexistent.ol", ["Empty result count: 0"]),
    # Wildcard + array combination: .*[*]
    ("test_wildcard_array_basic.ol", ["tree.a[1]", "tree.b[0]", "tree.b[1]"]),
    ("test_wildcard_array_multi_level.ol", ["data.x.beta[0]", "data.y.gamma[0]", "data.y.gamma[1]"]),
    ("test_wildcard_array_objects.ol", ["items.users[0]", "items.users[1]", "items.products[1]"]),
    ("test_wildcard_array_boolean.ol", ["data.x[1]", "data.y[1]"]),
    ("test_wildcard_array_empty.ol", ["Result count: 1", "tree.b[0]"]),
    ("test_wildcard_array_string.ol", ["colors.secondary[2]", "colors.primary[0]"]),
    # Complex wildcard array tests
    ("test_wildcard_array_complex_nesting.ol", ["High-price items:", "store.electronics[0]", "store.electronics[1]", "store.books[1]"]),
    ("test_wildcard_array_mixed_types.ol", ["Results:", "data.a[1]", "data.c[0]", "data.c[1]", "data.c[2]"]),
    ("test_wildcard_array_deep_multilevel.ol", ["Deep results:", "root.other.path.values[0]", "root.level1.alt.data[0]", "root.level1.alt.data[1]", "root.level1.level2.items[1]", "root.level1.level2.items[2]"]),
    ("test_wildcard_array_empty_arrays.ol", ["Results (empty arrays should be skipped):", "Count: 3", "data.single[0]", "data.multiple[1]", "data.multiple[2]"]),
    ("test_wildcard_array_negation.ol", ["Values outside [10,20]:", "values.x[0]", "values.x[2]", "values.y[2]"]),
    ("test_wildcard_array_with_field_access.ol", ["High quantity items:", "inventory.warehouse1[1]", "inventory.warehouse2[1]"]),
    # Array wildcard in WHERE clause
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
    # Field wildcard in WHERE clause
    ("test_where_field_wildcard_basic.ol", ["data"]),
    ("test_where_field_wildcard_greater_than.ol", ["tree"]),
    ("test_where_field_wildcard_multi_level.ol", ["root"]),
    ("test_where_field_wildcard_with_field.ol", ["data"]),
    ("test_where_field_wildcard_string.ol", ["colors"]),
    ("test_where_field_wildcard_negation.ol", []),
    ("test_where_field_wildcard_nested.ol", ["store.items[2]"]),
    ("test_where_field_wildcard_boolean_and.ol", ["item"]),
    ("test_where_field_wildcard_deep.ol", ["root"]),
]

def run_test(test_tuple):
    test, expected = test_tuple
    out = run(f"JOLIE_HOME={root}/dist/jolie {root}/dist/launchers/unix/jolie test/select/{test}").stdout
    result = [line.strip() for line in out.strip().split('\n') if line.strip()]
    ok = result == expected
    return (test, expected, result, ok)

# Run tests concurrently
with concurrent.futures.ThreadPoolExecutor() as executor:
    results = list(executor.map(run_test, tests))

# Print results
passed = 0
for test, expected, result, ok in results:
    print(f"{'✓' if ok else '✗'} {test}")
    if not ok: print(f"  Expected: {expected}\n  Got: {result}")
    passed += ok

print(f"\n{passed}/{len(tests)} passed")
sys.exit(0 if passed == len(tests) else 1)
