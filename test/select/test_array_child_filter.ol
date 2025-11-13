// Test: Nested Navigation with Child Field Filter
// Expected: data[1].a (where .b==5)

include "console.iol"

main {
    data[0].a.b = 6;
    data[1].a.b = 5;

    paths "$[*].a" into results from data where ".b == 5";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
