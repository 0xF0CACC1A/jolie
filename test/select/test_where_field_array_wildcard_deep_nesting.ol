// Test deep nesting with field + array wildcard: $.*.*[*]

include "console.iol"

main {
    root.branch1.leaves[0] = 10;
    root.branch1.leaves[1] = 20;
    root.branch1.leaves[2] = 30;

    root.branch2.leaves[0] = 5;
    root.branch2.leaves[1] = 15;

    root.branch3.leaves[0] = 50;

    // Find root where any branch has any leaf > 25
    res << paths root where $.*.*[*] > 25;

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
