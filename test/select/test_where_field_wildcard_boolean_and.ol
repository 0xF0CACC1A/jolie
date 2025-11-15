// Test field wildcard with AND operator

include "console.iol"

main {
    item.a = 15;
    item.b = 18;
    item.c = 25;

    // Find item where any field is between 10 and 20
    res << paths item where $.* > 10 && $.* < 20;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
