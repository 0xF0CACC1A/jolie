// Test [*].* with no matches

include "console.iol"

main {
    data[0].x = 10;
    data[0].y = 20;
    data[1].x = 30;
    data[1].y = 40;

    // Get only values > 1000 (should be empty)
    res << paths data[*].* where $ > 1000;

    println@Console("Result count: " + #res.results)();
    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
