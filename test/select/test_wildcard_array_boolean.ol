// Test wildcard array with boolean operators

include "console.iol"

main {
    data.x[0] = 5;
    data.x[1] = 15;
    data.x[2] = 25;
    data.y[0] = 8;
    data.y[1] = 12;
    data.y[2] = 30;

    res << paths data.*[*] where $ > 10 && $ < 20;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
