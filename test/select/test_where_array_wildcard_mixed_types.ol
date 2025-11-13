// Test array wildcard with mixed types (int and string)

include "console.iol"

main {
    data.records[0].values[0] = 10;
    data.records[0].values[1] = 20;

    data.records[1].values[0] = "text";
    data.records[1].values[1] = "data";

    data.records[2].values[0] = 15;
    data.records[2].values[1] = 25;

    // Numeric comparison
    res << paths data.records[*] where $.values[*] > 12;

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
