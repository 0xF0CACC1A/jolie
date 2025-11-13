// Test array wildcard with string comparisons

include "console.iol"

main {
    data[0] = "apple";
    data[1] = "banana";
    data[2] = "cherry";

    res << paths data[*] where $ == "banana";

    for (i = 0, i < #res.results, i++) {
        println@Console(res.results[i])()
    }
}
