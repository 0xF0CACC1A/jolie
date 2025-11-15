// Test basic field + array wildcard combination: $.*[*]

include "console.iol"

main {
    data.x[0] = 5;
    data.x[1] = 15;
    data.y[0] = 25;
    data.y[1] = 35;

    // Find data where any field has any array element > 20
    res << paths data where $.*[*] > 20;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
