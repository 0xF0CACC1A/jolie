// Test array wildcard with nonexistent field - should not match or vivify

include "console.iol"

main {
    data.items[0].name = "Item1";
    data.items[0].tags[0] = "red";
    data.items[0].tags[1] = "blue";

    data.items[1].name = "Item2";
    // No tags field

    data.items[2].name = "Item3";
    data.items[2].tags[0] = "green";

    res << paths data.items[*] where $.tags[*] == "red";

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
