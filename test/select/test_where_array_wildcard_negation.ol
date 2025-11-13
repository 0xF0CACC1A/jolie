// Test array wildcard with negation: $.tags[*] != "purple"
// Should match items where ANY tag doesn't equal "purple" (existential quantifier)

include "console.iol"

main {
    data.items[0].tags[0] = "red";
    data.items[0].tags[1] = "blue";

    data.items[1].tags[0] = "green";
    data.items[1].tags[1] = "purple";

    data.items[2].tags[0] = "yellow";
    data.items[2].tags[1] = "orange";

    res << paths data.items[*] where $.tags[*] != "purple";

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
