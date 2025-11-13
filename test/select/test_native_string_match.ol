// Test: Native PATHS with string comparison
// Expected output: fruits.c, fruits.a

include "console.iol"

main {
    fruits.a = "apple";
    fruits.b = "banana";
    fruits.c = "apple";

    res << paths fruits.* where $ == "apple";

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
