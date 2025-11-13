// Test array wildcard in both PATHS path and WHERE clause
// paths data.items[*] where $.subitems[*].value > 10

include "console.iol"

main {
    data.items[0].name = "Item1";
    data.items[0].subitems[0].value = 5;
    data.items[0].subitems[1].value = 15;

    data.items[1].name = "Item2";
    data.items[1].subitems[0].value = 8;
    data.items[1].subitems[1].value = 9;

    data.items[2].name = "Item3";
    data.items[2].subitems[0].value = 12;
    data.items[2].subitems[1].value = 20;

    res << paths data.items[*] where $.subitems[*].value > 10;

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
