// Test array wildcard with inequality: $.tags[*] != 5
// Should match if ANY tag doesn't equal 5

include "console.iol"

main {
    data.a.tags[0] = 5;
    data.a.tags[1] = 6;
    data.b.tags[0] = 6;
    data.b.tags[1] = 7;

    res << paths data.* where $.tags[*] != 5;

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
