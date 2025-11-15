// Test wildcard array with string comparisons

include "console.iol"

main {
    colors.primary[0] = "red";
    colors.primary[1] = "blue";
    colors.primary[2] = "green";
    colors.secondary[0] = "orange";
    colors.secondary[1] = "purple";
    colors.secondary[2] = "red";

    res << paths colors.*[*] where $ == "red";

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
