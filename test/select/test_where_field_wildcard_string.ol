// Test field wildcard with string comparison

include "console.iol"

main {
    colors.primary = "red";
    colors.secondary = "blue";
    colors.accent = "green";

    // Find colors where any field equals "blue"
    res << paths colors where $.* == "blue";

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
