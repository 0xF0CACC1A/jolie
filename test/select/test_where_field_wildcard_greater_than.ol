// Test field wildcard with greater than operator

include "console.iol"

main {
    tree.x = 5;
    tree.y = 15;
    tree.z = 25;

    // Find tree where any field > 20
    res << paths tree where $.* > 20;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
