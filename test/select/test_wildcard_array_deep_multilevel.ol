// Test very deep nesting with .*.*.*[*]

include "console.iol"

main {
    root.level1.level2.items[0] = 100;
    root.level1.level2.items[1] = 200;
    root.level1.level2.items[2] = 300;

    root.level1.level2.other[0] = 50;

    root.level1.alt.data[0] = 150;
    root.level1.alt.data[1] = 250;

    root.other.path.values[0] = 400;

    // Three levels of wildcards then array
    res << paths root.*.*.*[*] where $ > 100;

    println@Console("Deep results:")();
    for (i = 0, i < #res.results, i++) {
        println@Console("  " + res.results[i])()
    }
}
