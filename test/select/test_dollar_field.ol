// Test: Native SELECT with $.field in WHERE clause
// Expected output: tree.c, tree.b

include "console.iol"

main {
    tree.a.value = 5;
    tree.b.value = 15;
    tree.c.value = 20;

    // Should return tree.b and tree.c since their .value > 10
    res << select tree.* where $.value > 10;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
