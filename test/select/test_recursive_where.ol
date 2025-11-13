// Test: SELECT with recursive field in WHERE clause ($..field)
// Expected output: tree.b

include "console.iol"

main {
    tree.a.data.score = 5;
    tree.b.info.score = 15;
    tree.c.other = 20;

    // Should return tree.b since it has a descendant field "score" > 10
    res << select tree.* where $..score > 10;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
