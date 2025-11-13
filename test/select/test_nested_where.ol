// Test: Nested path traversal in WHERE
// Expected: data[1].a (has b.c==5)

include "console.iol"

main {
    data[0].a.id = 1;
    data[0].a.b.c = 6;

    data[1].a.id = 2;
    data[1].a.b.c = 5;

    data[2].a.id = 3;

    data[3].a.id = 4;
    data[3].a.b.x = 10;

    paths "$[*].a" into results from data where ".b.c == 5";

    i = 0;
    while( i < #results ) {
        println@Console(results[i])();
        i++
    }
}
