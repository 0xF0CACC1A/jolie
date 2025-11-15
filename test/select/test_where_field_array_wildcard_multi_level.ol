// Test multi-level field + array wildcard: $.*.items[*]

include "console.iol"

main {
    data.store1.items[0].price = 10;
    data.store1.items[1].price = 50;
    data.store1.items[2].price = 15;

    data.store2.items[0].price = 25;
    data.store2.items[1].price = 100;

    data.store3.items[0].price = 5;

    // Find data where any store has any item with price > 75
    res << paths data where $.*.items[*].price > 75;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
