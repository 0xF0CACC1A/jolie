// Test with empty arrays and single-element arrays

include "console.iol"

main {
    // Single element
    data.single[0] = 42;

    // Multiple elements
    data.multiple[0] = 10;
    data.multiple[1] = 20;
    data.multiple[2] = 30;

    res << paths data.*[*] where $ > 15;

    println@Console("Results (empty arrays should be skipped):")();
    println@Console("Count: " + #res.results)();
    for (i = 0, i < #res.results, i++) {
        println@Console("  " + res.results[i])()
    }
}
