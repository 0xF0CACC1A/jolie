// Test basic field wildcard in WHERE clause: $.*

include "console.iol"

main {
    data.a = 10;
    data.b = 20;
    data.c = 5;

    // Find data items where any field equals 5
    res << paths data where $.* == 5;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
