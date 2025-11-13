// Test that old "select" keyword no longer works
// This should fail to parse

include "console.iol"

main {
    tree.a = 5;
    tree.b = 10;

    // Using old "select" keyword - should cause parse error
    res << select tree.* where $ > 0;

    println@Console("This should not print")();
}
