// Test: Native PATHS with simple value comparison
// Expected output: data.z, data.x

include "console.iol"

main {
    data.x = 100;
    data.y = 200;
    data.z = 100;

    res << paths data.* where $ == 100;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
