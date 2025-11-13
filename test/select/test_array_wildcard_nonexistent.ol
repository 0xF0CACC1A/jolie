// Test array wildcard with nonexistent field - should not vivify

include "console.iol"

main {
    tree.other = "value";
    // tree.items doesn't exist

    res << paths tree.items[*] where $ > 0;

    println@Console("Empty result count: " + #res.results)()
}
