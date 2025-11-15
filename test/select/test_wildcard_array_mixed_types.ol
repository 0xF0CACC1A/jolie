// Test mixed types: some children are arrays, some are scalars, some don't exist

include "console.iol"

main {
    data.a[0] = 10;
    data.a[1] = 20;
    data.b = "scalar";  // Not an array
    data.c[0] = 30;
    data.c[1] = 40;
    data.c[2] = 50;
    // data.d doesn't exist
    data.e.nested = "value";  // Nested structure, not array

    // Should only enumerate actual arrays
    res << paths data.*[*] where $ > 15;

    println@Console("Results:")();
    for (i = 0, i < #res.results, i++) {
        println@Console("  " + res.results[i])()
    }
}
