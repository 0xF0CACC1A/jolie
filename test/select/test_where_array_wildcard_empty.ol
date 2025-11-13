// Test array wildcard with empty array - should not match

include "console.iol"

main {
    // items[0] has empty tags array
    data.items[0].name = "Item1";

    // items[1] has tags with "red"
    data.items[1].name = "Item2";
    data.items[1].tags[0] = "red";

    res << paths data.items[*] where $.tags[*] == "red";

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
