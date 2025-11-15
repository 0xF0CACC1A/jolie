// Test array wildcard: var.field[*]
// Should select all array elements

include "console.iol"

main {
    tree.items[0] = 10;
    tree.items[1] = 20;
    tree.items[2] = 30;
    tree.other = "value";

    res << paths tree.items[*] where $ > 15;

    println@Console("Results:")();
    for (i = 0, i < #res.results, i++) {
        println@Console("  " + res.results[i])()
    }
}
