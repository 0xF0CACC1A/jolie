// Test: Native PATHS with single variable (no wildcard)
// Expected output: myvar

include "console.iol"

main {
    myvar = 5;

    // Should return ["myvar"] since myvar == 5
    res << paths myvar where $ == 5;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
