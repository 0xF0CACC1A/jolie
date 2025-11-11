// Test: Descendant Search
// Expected output: tree.a.value, tree.b.c.value

include "console.iol"

main {
    tree.a.value = 42;
    tree.b.c.value = 42;

    select "$..value" into results from tree where ". == 42";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
