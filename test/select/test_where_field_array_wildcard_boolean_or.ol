// Test field + array wildcard with boolean OR

include "console.iol"

main {
    data.x[0] = 100;
    data.x[1] = 200;

    data.y[0] = 5;
    data.y[1] = 10;

    data.z[0] = 50;

    // Find data where some element > 150 OR some element < 10
    res << paths data where $.*[*] > 150 || $.*[*] < 10;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
