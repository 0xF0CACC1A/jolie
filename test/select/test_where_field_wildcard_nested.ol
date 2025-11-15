// Test field wildcard with nested paths clause

include "console.iol"

main {
    store.items[0].a = 50;
    store.items[0].b = 100;
    store.items[0].c = 25;
    store.items[1].a = 10;
    store.items[1].b = 20;
    store.items[1].c = 15;
    store.items[2].a = 75;
    store.items[2].b = 150;
    store.items[2].c = 80;

    // Find store items where any field > 100
    res << paths store.items[*] where $.* > 100;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
