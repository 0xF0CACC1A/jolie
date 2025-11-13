// Test array wildcard with boolean operators

include "console.iol"

main {
    data[0] = 5;
    data[1] = 15;
    data[2] = 25;
    data[3] = 35;

    res << paths data[*] where $ > 10 && $ < 30;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
