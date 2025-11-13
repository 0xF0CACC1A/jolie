// Test array wildcard on nested field: tree.items[*]

include "console.iol"

main {
    tree.items[0] = 5;
    tree.items[1] = 15;
    tree.items[2] = 25;
    tree.other = "value";

    res << paths tree.items[*] where $ > 10;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
