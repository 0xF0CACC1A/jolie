// Test basic array wildcard in WHERE clause: $.tags[*] == "red"

include "console.iol"

main {
    data.items[0].tags[0] = "red";
    data.items[0].tags[1] = "blue";
    data.items[1].tags[0] = "green";
    data.items[1].tags[1] = "yellow";
    data.items[2].tags[0] = "red";
    data.items[2].tags[1] = "green";

    res << paths data.items[*] where $.tags[*] == "red";

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
