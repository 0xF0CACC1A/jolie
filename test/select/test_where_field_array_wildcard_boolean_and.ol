// Test field + array wildcard with boolean AND

include "console.iol"

main {
    data.a[0] = 5;
    data.a[1] = 15;
    data.a[2] = 25;

    data.b[0] = 10;
    data.b[1] = 30;

    // Find data where some element > 20 AND some element < 10
    res << paths data where $.*[*] > 20 && $.*[*] < 10;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
