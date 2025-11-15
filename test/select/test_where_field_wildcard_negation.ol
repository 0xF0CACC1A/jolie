// Test field wildcard with NOT operator

include "console.iol"

main {
    values.x = 10;
    values.y = 20;
    values.z = 30;

    // Find values where NOT any field equals 20
    res << paths values where !($.* == 20);

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
