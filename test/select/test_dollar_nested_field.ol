// Test: Native PATHS with $.field.subfield in WHERE clause
// Expected output: items.c

include "console.iol"

main {
    items.a.data.score = 5;
    items.b.data.score = 8;
    items.c.data.score = 15;

    // Should return items.c since its .data.score > 10
    res << paths items.* where $.data.score > 10;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
