// Test: Native SELECT with string comparison
// Expected output: fruits.c, fruits.a

include "console.iol"

main {
    fruits.a = "apple";
    fruits.b = "banana";
    fruits.c = "apple";

    res << select fruits.* from fruits where $ == "apple";

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
