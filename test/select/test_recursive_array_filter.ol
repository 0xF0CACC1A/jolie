// Test ..field[*] with WHERE filtering

include "console.iol"

main {
    data.values[0] = 10;
    data.values[1] = 50;
    data.values[2] = 100;
    data.nested.values[0] = 5;
    data.nested.values[1] = 75;
    data.nested.deep.values[0] = 200;

    // Find all 'values' arrays recursively, get only values > 50
    res << paths data..values[*] where $ > 50;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
