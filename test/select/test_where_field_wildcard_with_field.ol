// Test field wildcard combined with specific field: $.*.value

include "console.iol"

main {
    data.a.value = 100;
    data.a.other = 5;
    data.b.value = 50;
    data.b.other = 10;
    data.c.value = 200;
    data.c.other = 15;

    // Find data where any child's value field > 150
    res << paths data where $.*.value > 150;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
