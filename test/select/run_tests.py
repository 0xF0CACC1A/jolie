#!/usr/bin/env python3
import subprocess
import sys
import os
import pathlib

# Get project root relative to this script
project_root = pathlib.Path(__file__).parent.parent.parent

def run(cmd, env=None):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=str(project_root), env=env)

# Build
print("Building project...")
result = run("mvn -q clean package -DskipTests -pl '!test'")
if result.returncode != 0:
    print("Build failed:", result.stderr)
    sys.exit(1)

# Set up dist directory
print("Setting up dist directory...")
run("rm -rf dist && mkdir -p dist/lib dist/javaServices dist/extensions")
run("cp libjolie/target/libjolie-*.jar dist/lib/libjolie.jar")
run("cp jolie/target/jolie-*.jar dist/jolie.jar")
run("cp jolie-cli/target/jolie-cli-*.jar dist/jolie-cli.jar")
run("cp test/select/antlr4-runtime-4.13.1.jar dist/lib/")
run("cp javaServices/coreJavaServices/target/coreJavaServices-*.jar dist/javaServices/")
run("cp extensions/jolie-embedding-legacy/target/jolie-embedding-legacy-*.jar dist/extensions/")
run("cp launchers/unix/jolie dist/jolie && chmod +x dist/jolie")
run("sed -i 's/:$JOLIE_HOME\\/lib\\/json-simple.jar:/:$JOLIE_HOME\\/lib\\/json-simple.jar:$JOLIE_HOME\\/lib\\/antlr4-runtime-4.13.1.jar:/' dist/jolie")

# Set up environment for dist/jolie
jolie_env = os.environ.copy()
jolie_env['JOLIE_HOME'] = str(project_root / "dist")

print("Running tests...\n")

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
    ("test_expression_equality.ol", ["root.z", "root.x", "---", "root.z", "root.x"]),
]

# Run tests
results = []
for test_file, expected in tests:
    result = run(f"dist/jolie test/select/{test_file}", env=jolie_env)
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
