// Test: SELECT with recursive descent (var..field)
// Expected output: tree.b.data.value, tree.a.value

include "console.iol"

main {
    tree.a.value = 5;
    tree.b.data.value = 15;
    tree.c.other = 20;

    // Should find all paths ending with "value" under tree
    res << select tree..value where $ > 0;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
