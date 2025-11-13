// Test array wildcard on base variable: data[*]

include "console.iol"

main {
    data[0] = 5;
    data[1] = 6;
    data[2] = 3;

    res << paths data[*] where $ > 5;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
