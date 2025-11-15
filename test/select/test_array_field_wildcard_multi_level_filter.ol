// Test [*].*.* with filtering

include "console.iol"

main {
    data[0].a.x = 5;
    data[0].a.y = 150;
    data[0].b.z = 10;
    data[1].a.x = 200;
    data[1].b.z = 20;

    // Get only values > 100
    res << paths data[*].*.* where $ > 100;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
