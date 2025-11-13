// Test: Native PATHS with single variable that doesn't match
// Expected output: (empty - no output)

include "console.iol"

main {
    myvar = 10;

    // Should return [] since myvar is 10, not 5
    res << paths myvar where $ == 5;

    // Should print nothing since no results
    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
