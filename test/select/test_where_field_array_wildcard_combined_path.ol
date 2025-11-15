// Test field + array wildcard in WHERE with array wildcard in path

include "console.iol"

main {
    root[0].a[0] = 10;
    root[0].a[1] = 20;
    root[0].b[0] = 5;

    root[1].a[0] = 100;
    root[1].a[1] = 200;
    root[1].b[0] = 50;

    // Find root array elements where any field has any array element > 150
    res << paths root[*] where $.*[*] > 150;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
