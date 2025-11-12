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

print("Running native SELECT tests...\n")

# Test cases: (filename, [expected output lines])
# Tests for native SELECT syntax (var.* instead of "$.*")
tests = [
    ("test_native_wildcard.ol", ["tree.c", "tree.a"]),
    ("test_native_simple_value.ol", ["data.z", "data.x"]),
    ("test_native_greater_than.ol", ["items.c", "items.b"]),
    ("test_native_string_match.ol", ["fruits.c", "fruits.a"]),
    ("test_native_not_equal.ol", ["vals.c", "vals.a"]),
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
print("Native SELECT Syntax Tests")
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
