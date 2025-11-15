// Test field + array wildcard with negation

include "console.iol"

main {
    data.x[0] = 10;
    data.x[1] = 20;

    data.y[0] = 30;
    data.y[1] = 40;

    // Find data where NO field has any array element == 100
    res << paths data where !($.*[*] == 100);

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
