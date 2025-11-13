// Test: Simple Path Navigation with WHERE
// Expected output: a.b.c

include "console.iol"

main {
    a.b.c = 5;

    paths "$.b.c" into results from a where ". == 5";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
