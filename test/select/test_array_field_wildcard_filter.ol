// Test [*].* with filtering WHERE condition

include "console.iol"

main {
    data[0].x = 10;
    data[0].y = 100;
    data[1].x = 30;
    data[1].y = 5;

    // Get only fields > 50
    res << paths data[*].* where $ > 50;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
