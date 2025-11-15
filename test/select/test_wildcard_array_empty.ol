// Test wildcard array with non-existent fields (vivification check)
// Should not create paths, should handle gracefully

include "console.iol"

main {
    tree.a = "scalar";
    tree.b[0] = 10;
    // tree.c doesn't exist
    // tree.d exists but is not an array

    res << paths tree.*[*] where $ > 5;

    println@Console("Result count: " + #res.results)();
    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
