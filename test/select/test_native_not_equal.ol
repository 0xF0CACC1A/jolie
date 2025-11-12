// Test: Native SELECT with not equal comparison
// Expected output: vals.c, vals.a

include "console.iol"

main {
    vals.a = 1;
    vals.b = 2;
    vals.c = 3;

    res << select vals.* where $ != 2;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
