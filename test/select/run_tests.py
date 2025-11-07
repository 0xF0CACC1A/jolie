#!/usr/bin/env python3
import subprocess
import sys

def run(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd="/home/matteo/JOLIE/jolie")

# Clean build
result = run("mvn -q clean compile -DskipTests")
if result.returncode != 0:
    print("Build failed:", result.stderr)
    sys.exit(1)

# Classpath with local ANTLR JAR
CP = "libjolie/target/classes:jolie/target/classes:jolie-cli/target/classes:libjolie/target/generated-sources/antlr4:test/select/antlr4-runtime-4.13.1.jar"

# Test cases: (filename, [expected output lines])
# Note: Order matches stack-based traversal output
tests = [
    ("test_wildcard_where.ol", ["root.z", "root.x"]),
    ("test_simple_path.ol", ["a.b.c"]),
    ("test_descendant_search.ol", ["tree.b.c.value", "tree.a.value"]),
    ("test_nested_path.ol", ["data.items.z", "data.items.x"]),
    ("test_array_and.ol", ["items[3]", "items[0]"]),
    ("test_array_or.ol", ["items[3]", "items[0]"]),
    ("test_array_not.ol", ["items[1]"]),
    ("test_array_parentheses.ol", ["items[2]", "items[1]", "items[0]"]),
    ("test_field_existence.ol", ["items[0]"]),
    ("test_nested_where.ol", ["data[1].a"]),
    ("test_array_descendant.ol", ["companies[0].projects[0]"]),
    ("test_nested_path_existence.ol", ["users[0]"]),
    ("test_direct_field.ol", ["projects[0]"]),
    ("test_multilevel_path.ol", ["accounts[0]"]),
    ("test_array_child_filter.ol", ["data[1].a"]),
    ("test_composed_descendant.ol", ["companies[0].departments[1].teams.projects[0].status", "companies[0].departments[0].teams.projects[0].status"]),
    ("test_companies.ol", ["companies[2].departments.teams.projects[1]", "companies[0].departments.teams.projects[0]"]),
]

# Run tests
results = []
for test_file, expected in tests:
    result = run(f"java -cp {CP} jolie.Jolie test/select/{test_file}")
    output = [line.strip() for line in result.stdout.strip().split('\n') if line.strip()]
    passed = output == expected
    results.append((test_file, passed, expected, output))

# Report
print(f"\n{'='*60}")
print("SELECT Primitive Tests")
print(f"{'='*60}\n")

for test_file, passed, expected, output in results:
    status = '✓' if passed else '✗'
    print(f"{status} {test_file}")
    if not passed:
        print(f"  Expected: {expected}")
        print(f"  Got:      {output}")

print(f"\n{'='*60}")
passed_count = sum(1 for _, p, _, _ in results if p)
total_count = len(results)
all_passed = passed_count == total_count

if all_passed:
    print(f"✓ ALL PASSED ({passed_count}/{total_count})")
else:
    print(f"✗ FAILED ({passed_count}/{total_count} passed)")
print(f"{'='*60}\n")

exit(0 if all_passed else 1)
