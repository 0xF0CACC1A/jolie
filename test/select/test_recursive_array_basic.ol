// Test ..field[*] - basic recursive descent + array wildcard

include "console.iol"

main {
    data.items[0] = 10;
    data.items[1] = 20;
    data.nested.items[0] = 30;
    data.nested.items[1] = 40;
    data.nested.deep.items[0] = 50;

    // Find all 'items' arrays recursively, enumerate all elements
    res << paths data..items[*] where true;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
