// Test [*].* with inequality

include "console.iol"

main {
    data[0].min = 10;
    data[0].max = 100;
    data[1].min = 50;
    data[1].max = 200;

    // Get only values < 75
    res << paths data[*].* where $ < 75;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
