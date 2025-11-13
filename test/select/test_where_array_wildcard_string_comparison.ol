// Test array wildcard with string comparison
// Both equality and inequality

include "console.iol"

main {
    data.documents[0].labels[0] = "draft";
    data.documents[0].labels[1] = "internal";

    data.documents[1].labels[0] = "published";
    data.documents[1].labels[1] = "public";

    data.documents[2].labels[0] = "draft";
    data.documents[2].labels[1] = "review";

    // Test equality
    res << paths data.documents[*] where $.labels[*] == "draft";

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
