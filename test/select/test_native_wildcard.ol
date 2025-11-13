// Test: Native PATHS with var.* syntax (no ANTLR string)
// Expected output: tree.c, tree.a

include "console.iol"

main {
    tree.a = 5;
    tree.b = 6;
    tree.c = 5;

    res << paths tree.* where $ == 5;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
