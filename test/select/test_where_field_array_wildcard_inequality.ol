// Test field + array wildcard with inequality operators

include "console.iol"

main {
    data.a[0] = 10;
    data.a[1] = 20;
    data.a[2] = 30;

    data.b[0] = 50;
    data.b[1] = 60;

    // Find data where any field has any array element < 25
    res << paths data where $.*[*] < 25;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
