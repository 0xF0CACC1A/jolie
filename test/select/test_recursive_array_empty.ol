// Test ..field[*] with no matches

include "console.iol"

main {
    data.values[0] = 10;
    data.values[1] = 20;
    data.nested.values[0] = 30;

    // Find all 'values' arrays recursively, but no values > 1000
    res << paths data..values[*] where $ > 1000;

    println@Console("Result count: " + #res.results)();
    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
