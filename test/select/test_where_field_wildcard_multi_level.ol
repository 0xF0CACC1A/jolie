// Test multi-level field wildcard: $.*.*

include "console.iol"

main {
    root.a.x = 10;
    root.a.y = 25;
    root.b.z = 15;
    root.c.w = 5;

    // Find root where any child has any field > 20
    res << paths root where $.*.* > 20;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
