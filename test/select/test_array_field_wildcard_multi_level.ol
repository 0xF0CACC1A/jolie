// Test [*].*.* - multi-level wildcards after array

include "console.iol"

main {
    data[0].a.x = 10;
    data[0].a.y = 20;
    data[0].b.z = 30;
    data[1].a.x = 40;
    data[1].b.z = 50;

    res << paths data[*].*.* where true;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
