// Test: Native SELECT with greater than comparison
// Expected output: items.c, items.b

include "console.iol"

main {
    items.a = 5;
    items.b = 15;
    items.c = 20;

    res << select items.* from items where $ > 10;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
