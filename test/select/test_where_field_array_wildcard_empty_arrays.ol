// Test field + array wildcard with empty arrays

include "console.iol"

main {
    // Empty array should be skipped

    data.a[0] = 10;
    data.a[1] = 20;

    // data.b is not an array
    data.b = 30;

    data.c[0] = 40;

    // Find data where any field has any array element > 15
    res << paths data where $.*[*] > 15;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
